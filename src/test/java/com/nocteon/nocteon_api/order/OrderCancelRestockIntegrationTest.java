package com.nocteon.nocteon_api.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.address.entity.Address;
import com.nocteon.nocteon_api.address.repository.AddressRepository;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.enums.Provider;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.category.repository.CategoryRepository;
import com.nocteon.nocteon_api.order.dto.request.UpdateOrderStatusRequest;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.entity.OrderItem;
import com.nocteon.nocteon_api.order.entity.OrderStatusHistory;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.order.exception.InvalidOrderStatusTransitionException;
import com.nocteon.nocteon_api.order.repository.OrderItemRepository;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.order.repository.OrderStatusHistoryRepository;
import com.nocteon.nocteon_api.order.service.OrderStatusService;
import com.nocteon.nocteon_api.payment.enums.PaymentMethod;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;
import com.nocteon.nocteon_api.product.entity.InventoryMovement;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.enums.InventoryMovementReason;
import com.nocteon.nocteon_api.product.enums.InventoryMovementSource;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.repository.InventoryMovementRepository;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;
import com.nocteon.nocteon_api.product.service.InventoryMovementService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

import jakarta.persistence.EntityManager;

/**
 * A cancelled order puts back the stock it actually took — and nothing else.
 *
 * <p>The bug this pins: a cash-on-delivery order is CONFIRMED at checkout and
 * its units are CONSUMED (off the shelf). Cancelling it only released the
 * RESERVATION (already zero after the consumption, so a no-op), and the units
 * never came back: the shelf said 7 forever for an order that had died.
 *
 * <p>What a mock cannot prove, and this does, against the real database and the
 * real ledger:
 *
 * <ul>
 *   <li>the units are back on the shelf, line by line, exactly what was taken;</li>
 *   <li>the restock writes ONE movement per variant — reason
 *       {@code ORDER_CANCELLED}, source {@code ORDER}, the cancellation sentence,
 *       the actor, and the order id a reader can open;</li>
 *   <li>cancelling twice writes nothing the second time;</li>
 *   <li>an order that never consumed stock — unpaid (only reserved), or confirmed
 *       without a consumption row — restocks NOTHING. Its stock must not grow:
 *       inventing units is worse than the bug being fixed;</li>
 *   <li>the status, the history and the single {@code ORDER_CANCEL} audit row are
 *       otherwise unchanged, with the restock visible in that same row.</li>
 * </ul>
 *
 * <p>Every row is rolled back at the end of the test (class-level
 * {@code @Transactional}).
 */
@SpringBootTest(
        classes = NocteonApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class OrderCancelRestockIntegrationTest {

    /** The sentence a cancellation has to carry; it also becomes the movement's note. */
    private static final String CANCELLATION_REASON = "Customer changed their mind";
    private static final String ADMIN = "admin@nocteon.test";
    private static final String BUYER = "buyer@nocteon.test";

    @Autowired private OrderStatusService orderStatusService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private OrderStatusHistoryRepository historyRepository;
    @Autowired private InventoryMovementService movements;
    @Autowired private InventoryMovementRepository movementRepository;
    @Autowired private ProductVariantRepository variantRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    private String suffix;
    private User customer;
    private Address address;
    private Product product;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);

        customer = userRepository.save(User.builder()
                .username("restock-customer-" + suffix)
                .email("restock-customer-" + suffix + "@example.test")
                .password("x")
                .provider(Provider.LOCAL)
                .role("CUSTOMER")
                .enabled(true)
                .build());

        address = addressRepository.save(Address.builder()
                .user(customer)
                .fullName("Sara Ahmed")
                .phone("+201000000000")
                .street("12 Nile Street")
                .city("Cairo")
                .country("Egypt")
                .postalCode("11511")
                .isDefault(true)
                .build());

        Category category = categoryRepository.save(
                Category.builder().slug("restock-cat-" + suffix).isActive(true).build());
        product = Product.builder()
                .slug("restock-product-" + suffix)
                .category(category)
                .productType(ProductType.COFFEE)
                .isActive(true)
                .build();
        product.getTranslations().add(ProductTranslation.builder()
                .product(product).language("en").name("Restock " + suffix).build());
        productRepository.save(product);
    }

    // ══════════════════════════════════════════════════════════════════════
    // The bug: a confirmed COD order consumed its units
    // ══════════════════════════════════════════════════════════════════════

    /**
     * A COD order that consumed 3 + 2 units gets exactly 3 + 2 back, once, with
     * one movement per line explaining it.
     */
    @Test
    void aCancelledCodOrderPutsItsStockBackExactlyOnce() {
        ProductVariant bags = variant("bags", 10);
        ProductVariant cups = variant("cups", 5);

        Order order = savedOrder(PaymentMethod.CASH_ON_DELIVERY, PaymentStatus.PENDING, OrderStatus.CONFIRMED,
                line(bags, 3), line(cups, 2));
        // The checkout, faithfully: reserve the units, then consume them (a COD
        // order is CONFIRMED at checkout and its stock leaves the shelf).
        checkout(bags, 3, order);
        checkout(cups, 2, order);
        assertThat(stockOf(bags)).as("the checkout took the bags off the shelf").isEqualTo(7);
        assertThat(stockOf(cups)).as("the checkout took the cups off the shelf").isEqualTo(3);

        cancel(order);

        // ── the shelf ────────────────────────────────────────────────────
        assertThat(stockOf(bags)).as("3 bags are back on the shelf").isEqualTo(10);
        assertThat(stockOf(cups)).as("2 cups are back on the shelf").isEqualTo(5);

        // ── the ledger: one consumption + one restock per variant ─────────
        assertThat(movementRepository.countByVariantId(bags.getId()))
                .as("bags: the consumption AND the restock, and nothing else")
                .isEqualTo(2);
        assertThat(movementRepository.countByVariantId(cups.getId()))
                .as("cups: the consumption AND the restock, and nothing else")
                .isEqualTo(2);

        InventoryMovement restock = restockRow(bags);
        assertThat(restock.getReason())
                .as("the row says WHY the number changed")
                .isEqualTo(InventoryMovementReason.ORDER_CANCELLED);
        assertThat(restock.getSourceType())
                .as("the source is the order, so the log can jump to it")
                .isEqualTo(InventoryMovementSource.ORDER);
        assertThat(restock.getSourceId()).isEqualTo(order.getId());
        assertThat(restock.getStockBefore()).isEqualTo(7);
        assertThat(restock.getStockAfter()).isEqualTo(10);
        assertThat(restock.getDelta()).isEqualTo(3);
        assertThat(restock.getReasonNote())
                .as("the sentence is the cancellation reason the admin typed")
                .isEqualTo(CANCELLATION_REASON);
        assertThat(restock.getActor()).isEqualTo(ADMIN);

        // ── the order is otherwise unchanged ─────────────────────────────
        assertThat(statusOf(order)).isEqualTo("CANCELLED");
        assertThat(order.getCancellationReason()).isEqualTo(CANCELLATION_REASON);
        assertThat(order.getShippedAt()).isNull();

        List<OrderStatusHistory> history = historyRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());
        assertThat(history).as("placed + cancelled: the restock adds no history row").hasSize(2);
        OrderStatusHistory cancellation = history.stream()
                .filter(row -> "CANCELLED".equals(row.getToStatus()))
                .findFirst()
                .orElseThrow();
        assertThat(cancellation.getFromStatus()).isEqualTo("CONFIRMED");
        assertThat(cancellation.getChangedBy()).isEqualTo(ADMIN);

        // ── one audit action, and it mentions the restock ────────────────
        assertThat(successfulAudits("ORDER_CANCEL", order.getId()))
                .as("the cancellation is ONE audit row")
                .isEqualTo(1);
        assertThat(successfulAudits("ORDER_STATUS_CHANGE", order.getId()))
                .as("the restock did not become a second action")
                .isZero();
        assertThat(auditMetadata(order.getId()))
                .containsEntry("restocked", true)
                .containsEntry("restockedUnits", 5)
                .containsEntry("restockReason", InventoryMovementReason.ORDER_CANCELLED.name());
    }

    /**
     * A paid ONLINE order consumed its units too (the payment webhook does it),
     * so cancelling it restocks them for the same reason. This is the path the
     * payment method alone would have missed.
     */
    @Test
    void aPaidOnlineOrderThatConsumedStockIsRestockedToo() {
        ProductVariant bags = variant("paid-bags", 4);
        Order order = savedOrder(PaymentMethod.ONLINE, PaymentStatus.PAID, OrderStatus.CONFIRMED, line(bags, 4));
        checkout(bags, 4, order);
        assertThat(stockOf(bags)).isZero();

        cancel(order);

        assertThat(stockOf(bags)).isEqualTo(4);
        assertThat(restockRow(bags).getDelta()).isEqualTo(4);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Never twice, never what was not consumed
    // ══════════════════════════════════════════════════════════════════════

    /** A second cancellation is refused by the state machine and writes nothing. */
    @Test
    void aSecondCancellationRestocksNothing() {
        ProductVariant bags = variant("twice-bags", 10);
        Order order = savedOrder(PaymentMethod.CASH_ON_DELIVERY, PaymentStatus.PENDING, OrderStatus.CONFIRMED,
                line(bags, 3));
        checkout(bags, 3, order);

        cancel(order);
        long rowsAfterFirst = movementRepository.countByVariantId(bags.getId());
        assertThat(stockOf(bags)).isEqualTo(10);

        assertThatThrownBy(() -> orderStatusService.updateStatus(order.getId(), cancellation(), ADMIN))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        assertThat(stockOf(bags))
                .as("a cancelled order cannot be cancelled again, so the units stay put")
                .isEqualTo(10);
        assertThat(movementRepository.countByVariantId(bags.getId()))
                .as("and no second restock row appears")
                .isEqualTo(rowsAfterFirst);
        assertThat(restockRows(bags)).hasSize(1);
        assertThat(successfulAudits("ORDER_CANCEL", order.getId()))
                .as("only the first cancellation is recorded as a success")
                .isEqualTo(1);
    }

    /**
     * An unpaid order only RESERVED its units: cancelling it hands the
     * reservation back and touches no stock and no ledger.
     */
    @Test
    void anUnpaidOrderThatOnlyReservedStockIsNotRestocked() {
        ProductVariant bags = variant("unpaid-bags", 10);
        Order order = savedOrder(PaymentMethod.ONLINE, PaymentStatus.PENDING, OrderStatus.PENDING, line(bags, 2));
        // Checkout, without the payment: the units are promised, not taken.
        assertThat(variantRepository.reserveStockIfAvailable(bags.getId(), 2)).isEqualTo(1);
        entityManager.flush();
        assertThat(reservedOf(bags)).isEqualTo(2);
        assertThat(stockOf(bags)).isEqualTo(10);

        cancel(order);

        assertThat(stockOf(bags))
                .as("nothing was consumed, so nothing comes back: the shelf must not grow to 12")
                .isEqualTo(10);
        assertThat(reservedOf(bags))
                .as("the reservation is released, exactly as before this fix")
                .isZero();
        assertThat(movementRepository.countByVariantId(bags.getId()))
                .as("no consumption and no restock: the ledger stays empty")
                .isZero();
    }

    /**
     * A CONFIRMED COD order with no consumption row (a legacy order, or a
     * checkout whose consumption found nothing) restocks nothing.
     *
     * <p>The ledger decides, not the payment method: a cancellation may only
     * mirror units that actually left the shelf.
     */
    @Test
    void aConfirmedOrderWhoseConsumptionNeverHappenedIsNotRestocked() {
        ProductVariant bags = variant("legacy-bags", 10);
        Order order = savedOrder(PaymentMethod.CASH_ON_DELIVERY, PaymentStatus.PENDING, OrderStatus.CONFIRMED,
                line(bags, 3));

        cancel(order);

        assertThat(stockOf(bags)).isEqualTo(10);
        assertThat(movementRepository.countByVariantId(bags.getId())).isZero();
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    /** A line of the cart the order was built from. */
    private record Line(ProductVariant variant, int quantity) {
    }

    private static Line line(ProductVariant variant, int quantity) {
        return new Line(variant, quantity);
    }

    private ProductVariant variant(String prefix, int stock) {
        ProductVariant variant = variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku("RST-" + prefix + "-" + suffix)
                .price(new BigDecimal("650.00"))
                .stockQuantity(stock)
                .reorderThreshold(1)
                .isActive(true)
                .version(0L)
                .build());
        entityManager.flush();
        return variant;
    }

    /** An order as checkout leaves it: saved, with its items and its first history row. */
    private Order savedOrder(PaymentMethod method, PaymentStatus paymentStatus, OrderStatus status, Line... lines) {
        Order order = orderRepository.save(Order.builder()
                .orderNumber("ORD-RST-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 4))
                .user(customer)
                .address(address)
                .status(status)
                .paymentMethod(method)
                .paymentStatus(paymentStatus)
                .totalAmount(new BigDecimal("650.00").multiply(
                        BigDecimal.valueOf(java.util.Arrays.stream(lines).mapToInt(Line::quantity).sum())))
                .shippingCost(BigDecimal.ZERO)
                .items(new ArrayList<>())
                .build());

        for (Line line : lines) {
            OrderItem item = orderItemRepository.save(OrderItem.builder()
                    .order(order)
                    .productVariant(line.variant())
                    .quantity(line.quantity())
                    .unitPrice(line.variant().getPrice())
                    .totalPrice(line.variant().getPrice().multiply(BigDecimal.valueOf(line.quantity())))
                    .build());
            order.getItems().add(item);
        }
        entityManager.flush();
        orderStatusService.recordCreation(order, status, "system");
        entityManager.flush();
        return order;
    }

    /** The checkout's stock half: reserve the units, then consume them. */
    private void checkout(ProductVariant variant, int quantity, Order order) {
        assertThat(variantRepository.reserveStockIfAvailable(variant.getId(), quantity))
                .as("the units are reserved at checkout")
                .isEqualTo(1);
        InventoryMovementService.StockChange change =
                movements.consumeForOrder(variant.getId(), quantity, BUYER, order.getId());
        assertThat(change).as("a confirmed order consumes what it reserved").isNotNull();
        entityManager.flush();
    }

    private void cancel(Order order) {
        orderStatusService.updateStatus(order.getId(), cancellation(), ADMIN);
        entityManager.flush();
    }

    private static UpdateOrderStatusRequest cancellation() {
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.CANCELLED);
        request.setReason(CANCELLATION_REASON);
        return request;
    }

    // ── reads ────────────────────────────────────────────────────────────

    private int stockOf(ProductVariant variant) {
        return variantRepository.findStockById(variant.getId());
    }

    private int reservedOf(ProductVariant variant) {
        return jdbcTemplate.queryForObject(
                "select reserved_quantity from product_variants where id = ?", Integer.class, variant.getId());
    }

    private String statusOf(Order order) {
        return jdbcTemplate.queryForObject("select status from orders where id = ?", String.class, order.getId());
    }    private List<InventoryMovement> restockRows(ProductVariant variant) {
        return movements.recent(variant.getId(), InventoryMovementService.MAX_HISTORY).stream()
                .filter(row -> row.getReason() == InventoryMovementReason.ORDER_CANCELLED)
                .toList();
    }

    private InventoryMovement restockRow(ProductVariant variant) {
        List<InventoryMovement> rows = restockRows(variant);
        assertThat(rows).as("exactly one restock row for this variant").hasSize(1);
        return rows.get(0);
    }

    private int successfulAudits(String action, Long orderId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from public.audit_logs where action = ? and success = true "
                        + "and entity_type = 'Order' and entity_id = ?",
                Integer.class, action, orderId);
        return count == null ? 0 : count;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> auditMetadata(Long orderId) {
        String json = jdbcTemplate.queryForObject(
                "select metadata from public.audit_logs where action = 'ORDER_CANCEL' and success = true "
                        + "and entity_type = 'Order' and entity_id = ? order by id desc limit 1",
                String.class, orderId);
        if (json == null) {
            return Map.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            throw new AssertionError("the audit row's metadata does not parse: " + json, e);
        }
    }
}
