package com.nocteon.nocteon_api.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.address.entity.Address;
import com.nocteon.nocteon_api.address.repository.AddressRepository;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.enums.Provider;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.category.repository.CategoryRepository;
import com.nocteon.nocteon_api.order.dto.request.CreateReturnRequest;
import com.nocteon.nocteon_api.order.dto.response.OrderReturnResponse;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.entity.OrderItem;
import com.nocteon.nocteon_api.order.entity.OrderRefund;
import com.nocteon.nocteon_api.order.entity.OrderReturn;
import com.nocteon.nocteon_api.order.entity.OrderStatusHistory;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.order.enums.ReturnStatus;
import com.nocteon.nocteon_api.order.exception.InvalidReturnException;
import com.nocteon.nocteon_api.order.exception.ReturnAlreadyDecidedException;
import com.nocteon.nocteon_api.order.repository.OrderItemRepository;
import com.nocteon.nocteon_api.order.repository.OrderRefundRepository;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.order.repository.OrderReturnRepository;
import com.nocteon.nocteon_api.order.repository.OrderStatusHistoryRepository;
import com.nocteon.nocteon_api.order.service.OrderReturnService;
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
 * FE-20: returns / RMA, against the real database.
 *
 * <p>What a mock cannot prove, and this does:
 *
 * <ul>
 *   <li>the return is CAPPED by what is still returnable on the line — the
 *       ordered quantity minus what earlier, non-rejected returns brought back;</li>
 *   <li>APPROVING restocks the units through the inventory movement log (one
 *       row per line, reason RETURN, source RETURN) and writes the timeline
 *       entry in the same transaction;</li>
 *   <li>approving TWICE restocks once — a retry answers the row unchanged;</li>
 *   <li>REJECTING moves no stock, and the opposite decision afterwards is a 409;</li>
 *   <li>the endpoints are permission-gated and the list is scoped to its order.</li>
 * </ul>
 */
@SpringBootTest(classes = NocteonApiApplication.class)
@AutoConfigureMockMvc
@Transactional
class OrderReturnIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OrderReturnService returnService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderReturnRepository returnRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private OrderRefundRepository refundRepository;
    @Autowired
    private OrderStatusHistoryRepository historyRepository;
    @Autowired
    private ProductVariantRepository variantRepository;
    @Autowired
    private InventoryMovementRepository movementRepository;
    @Autowired
    private InventoryMovementService movementLog;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    private User admin;
    private Order order;
    private OrderItem line;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        admin = userRepository.save(User.builder()
                .username("return-admin-" + suffix)
                .email("return-admin-" + suffix + "@example.test")
                .password("x")
                .provider(Provider.LOCAL)
                .role("ADMIN")
                .enabled(true)
                .build());

        Category category = categoryRepository.save(
                Category.builder().slug("return-cat-" + suffix).isActive(true).build());

        Product product = Product.builder()
                .slug("return-product-" + suffix)
                .category(category)
                .productType(ProductType.COFFEE)
                .isActive(true)
                .build();
        product.getTranslations().add(ProductTranslation.builder()
                .product(product).language("en").name("Ethiopia Yirgacheffe " + suffix).build());
        productRepository.save(product);

        variant = variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku("RET-" + suffix)
                .price(new BigDecimal("650.00"))
                .stockQuantity(7)
                .reorderThreshold(3)
                .isActive(true)
                .version(0L)
                .build());

        Address address = addressRepository.save(Address.builder()
                .user(admin)
                .fullName("Return Tester")
                .phone("+201000000000")
                .street("1 Test Street")
                .city("Cairo")
                .country("Egypt")
                .isDefault(true)
                .build());

        order = orderRepository.saveAndFlush(Order.builder()
                .orderNumber("ORD-RET-" + suffix)
                .user(admin)
                .address(address)
                .status(OrderStatus.DELIVERED)
                .paymentStatus(PaymentStatus.PAID)
                .paymentMethod(PaymentMethod.ONLINE)
                .totalAmount(new BigDecimal("1950.00"))
                .shippingCost(BigDecimal.ZERO)
                .build());

        // Persisted on its own: a transient element handed to `save(order)`
        // goes through a MERGE, and the id lands on Hibernate's copy rather
        // than on the instance the test holds.
        line = orderItemRepository.saveAndFlush(OrderItem.builder()
                .order(order)
                .productVariant(variant)
                .quantity(3)
                .unitPrice(new BigDecimal("650.00"))
                .totalPrice(new BigDecimal("1950.00"))
                .build());
        order.getItems().add(line);
        entityManager.flush();
    }

    // ─── create ──────────────────────────────────────────────────────────────

    @Test
    void aReturnIsRecordedWithItsLinesAndItsReason() {
        OrderReturnResponse created = returnService.create(order.getId(), request(2, "Bag was torn"), admin.getEmail());

        assertThat(created.getId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo("REQUESTED");
        assertThat(created.getReason()).isEqualTo("The grind was wrong");
        assertThat(created.getRequestedBy()).isEqualTo(admin.getEmail());
        assertThat(created.getDecidedBy()).isNull();
        assertThat(created.getItems()).hasSize(1);

        OrderReturnResponse.Item item = created.getItems().get(0);
        assertThat(item.getOrderItemId()).isEqualTo(line.getId());
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getNote()).isEqualTo("Bag was torn");
        assertThat(item.getSku()).isEqualTo(variant.getSku());
        assertThat(item.getVariantId()).isEqualTo(variant.getId());

        // Recording a return does NOT move stock: only the approval does.
        assertThat(variantRepository.findStockById(variant.getId())).isEqualTo(7);
        assertThat(movementRepository.countByVariantId(variant.getId())).isZero();
    }

    @Test
    void aReturnWritesTheTimelineEntryInTheSameTransaction() {
        returnService.create(order.getId(), request(1, null), admin.getEmail());

        List<OrderStatusHistory> events = eventsOf(order.getId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo(OrderReturnService.EVENT_REQUESTED);
        assertThat(events.get(0).getFromStatus()).isNull();
        assertThat(events.get(0).getChangedBy()).isEqualTo(admin.getEmail());
        assertThat(events.get(0).getNote()).contains("1 ×").contains("Ethiopia Yirgacheffe");
    }

    @Test
    void moreUnitsThanWereOrderedAreRefused() {
        assertThatThrownBy(() -> returnService.create(order.getId(), request(4, null), admin.getEmail()))
                .isInstanceOf(InvalidReturnException.class)
                .hasMessage("error.return.quantityTooLarge");

        // Scoped to THIS test's order: the table is shared, and a return committed
        // by another run (or a manual check) must not fail a refusal test.
        assertThat(returnRepository.findByOrderIdOrderByCreatedAtDescIdDesc(order.getId())).isEmpty();
        assertThat(movementRepository.countByVariantId(variant.getId())).isZero();
    }

    /**
     * The cap is what is STILL returnable, not the ordered quantity: a second
     * return cannot bring back units the first one already returned.
     */
    @Test
    void theCapCountsWhatEarlierReturnsAlreadyBroughtBack() {
        returnService.create(order.getId(), request(2, null), admin.getEmail());

        assertThatThrownBy(() -> returnService.create(order.getId(), request(2, null), admin.getEmail()))
                .isInstanceOf(InvalidReturnException.class)
                .hasMessage("error.return.quantityTooLarge");

        // One more is still fine — 2 + 1 = 3.
        OrderReturnResponse second = returnService.create(order.getId(), request(1, null), admin.getEmail());
        assertThat(second.getItems().get(0).getQuantity()).isEqualTo(1);
    }

    @Test
    void aReasonIsRequired() {
        CreateReturnRequest noReason = request(1, null);
        noReason.setReason("   ");

        assertThatThrownBy(() -> returnService.create(order.getId(), noReason, admin.getEmail()))
                .isInstanceOf(InvalidReturnException.class)
                .hasMessage("error.return.reasonRequired");

        // Scoped to THIS test's order: the table is shared, and a return committed
        // by another run (or a manual check) must not fail a refusal test.
        assertThat(returnRepository.findByOrderIdOrderByCreatedAtDescIdDesc(order.getId())).isEmpty();
    }

    @Test
    void aCancelledOrderHasNothingToReturn() {
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        entityManager.flush();

        assertThatThrownBy(() -> returnService.create(order.getId(), request(1, null), admin.getEmail()))
                .isInstanceOf(InvalidReturnException.class)
                .hasMessage("error.return.orderCancelled");
    }

    @Test
    void aRefundOfAnotherOrderCannotBeTheLink() {
        Order other = orderRepository.save(Order.builder()
                .orderNumber("ORD-OTHER-" + UUID.randomUUID().toString().substring(0, 8))
                .user(admin)
                .address(order.getAddress())
                .status(OrderStatus.DELIVERED)
                .paymentStatus(PaymentStatus.REFUNDED)
                .paymentMethod(PaymentMethod.ONLINE)
                .totalAmount(new BigDecimal("10.00"))
                .shippingCost(BigDecimal.ZERO)
                .build());
        OrderRefund foreign = refundRepository.save(new OrderRefund(
                other, null, new BigDecimal("10.00"), "other order", admin.getEmail(), "ref-other"));

        CreateReturnRequest request = request(1, null);
        request.setRefundId(foreign.getId());

        assertThatThrownBy(() -> returnService.create(order.getId(), request, admin.getEmail()))
                .isInstanceOf(InvalidReturnException.class)
                .hasMessage("error.return.refundNotOnOrder");
    }

    /** A refund of THIS order is the link, and it comes back on the response. */
    @Test
    void aRefundOfTheSameOrderIsLinked() {
        OrderRefund refund = refundRepository.save(new OrderRefund(
                order, null, new BigDecimal("1300.00"), "two bags", admin.getEmail(), "ref-same"));

        CreateReturnRequest request = request(2, null);
        request.setRefundId(refund.getId());

        OrderReturnResponse created = returnService.create(order.getId(), request, admin.getEmail());
        assertThat(created.getRefundId()).isEqualTo(refund.getId());
    }

    // ─── approve ─────────────────────────────────────────────────────────────

    @Test
    void approvingRestocksThroughTheMovementLogAndWritesTheTimeline() {
        OrderReturnResponse created = returnService.create(order.getId(), request(2, null), admin.getEmail());

        OrderReturnResponse approved = returnService.decide(
                order.getId(), created.getId(), ReturnStatus.APPROVED, "approver@example.test");

        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        assertThat(approved.getDecidedBy()).isEqualTo("approver@example.test");
        assertThat(approved.getDecidedAt()).isNotNull();

        // The shelf moved: 7 + 2.
        assertThat(variantRepository.findStockById(variant.getId())).isEqualTo(9);

        // And the FE-13 log explains it — one row, with the real numbers. Read
        // THIS variant's rows only: scanning the whole table maps every row in
        // the database, and a legacy reason the enum no longer knows would fail
        // the read before the filter runs.
        List<InventoryMovement> rows = movementLog.recent(variant.getId(), InventoryMovementService.MAX_HISTORY);
        assertThat(rows).hasSize(1);
        InventoryMovement row = rows.get(0);
        assertThat(row.getStockBefore()).isEqualTo(7);
        assertThat(row.getStockAfter()).isEqualTo(9);
        assertThat(row.getDelta()).isEqualTo(2);
        assertThat(row.getReason()).isEqualTo(InventoryMovementReason.RETURN);
        assertThat(row.getSourceType()).isEqualTo(InventoryMovementSource.RETURN);
        assertThat(row.getSourceId()).isEqualTo(created.getId());
        assertThat(row.getActor()).isEqualTo("approver@example.test");
        assertThat(row.getReasonNote()).isEqualTo("The grind was wrong");

        // The timeline carries it, in the same table the status chain uses.
        assertThat(eventsOf(order.getId()))
                .extracting(OrderStatusHistory::getEventType)
                .containsExactly(OrderReturnService.EVENT_REQUESTED, OrderReturnService.EVENT_APPROVED);
    }

    /**
     * The retry: a second approval answers the row and restocks NOTHING.
     *
     * <p>This is the failure a naive implementation ships — an admin clicks
     * twice, or a request is retried after a dropped response, and the stock is
     * moved back twice.
     */
    @Test
    void approvingTwiceRestocksOnce() {
        OrderReturnResponse created = returnService.create(order.getId(), request(2, null), admin.getEmail());

        returnService.decide(order.getId(), created.getId(), ReturnStatus.APPROVED, "first@example.test");
        OrderReturnResponse second = returnService.decide(
                order.getId(), created.getId(), ReturnStatus.APPROVED, "second@example.test");

        assertThat(second.getStatus()).isEqualTo("APPROVED");
        // The FIRST decision stands: the retry does not overwrite who decided.
        assertThat(second.getDecidedBy()).isEqualTo("first@example.test");

        assertThat(variantRepository.findStockById(variant.getId())).isEqualTo(9);
        assertThat(movementRepository.countByVariantId(variant.getId())).isEqualTo(1);

        // And no second timeline row was written for the retry.
        assertThat(eventsOf(order.getId()))
                .extracting(OrderStatusHistory::getEventType)
                .containsExactly(OrderReturnService.EVENT_REQUESTED, OrderReturnService.EVENT_APPROVED);
    }

    @Test
    void rejectingMovesNoStock() {
        OrderReturnResponse created = returnService.create(order.getId(), request(2, null), admin.getEmail());

        OrderReturnResponse rejected = returnService.decide(
                order.getId(), created.getId(), ReturnStatus.REJECTED, admin.getEmail());

        assertThat(rejected.getStatus()).isEqualTo("REJECTED");
        assertThat(rejected.getDecidedAt()).isNotNull();
        assertThat(variantRepository.findStockById(variant.getId())).isEqualTo(7);
        assertThat(movementRepository.countByVariantId(variant.getId())).isZero();
        assertThat(eventsOf(order.getId()))
                .extracting(OrderStatusHistory::getEventType)
                .containsExactly(OrderReturnService.EVENT_REQUESTED, OrderReturnService.EVENT_REJECTED);
    }

    @Test
    void approvingARejectedReturnIsRefusedAndMovesNothing() {
        OrderReturnResponse created = returnService.create(order.getId(), request(2, null), admin.getEmail());
        returnService.decide(order.getId(), created.getId(), ReturnStatus.REJECTED, admin.getEmail());

        assertThatThrownBy(() -> returnService.decide(
                order.getId(), created.getId(), ReturnStatus.APPROVED, admin.getEmail()))
                .isInstanceOf(ReturnAlreadyDecidedException.class);

        assertThat(variantRepository.findStockById(variant.getId())).isEqualTo(7);
        assertThat(movementRepository.countByVariantId(variant.getId())).isZero();
    }

    /** A rejected return brought nothing back, so its units are still returnable. */
    @Test
    void aRejectedReturnGivesTheUnitsBackToTheReturnablePool() {
        OrderReturnResponse first = returnService.create(order.getId(), request(3, null), admin.getEmail());
        returnService.decide(order.getId(), first.getId(), ReturnStatus.REJECTED, admin.getEmail());

        OrderReturnResponse second = returnService.create(order.getId(), request(3, null), admin.getEmail());
        assertThat(second.getItems().get(0).getQuantity()).isEqualTo(3);
    }

    // ─── the API ─────────────────────────────────────────────────────────────

    @Test
    void theListIsScopedToItsOrder() throws Exception {
        returnService.create(order.getId(), request(1, null), admin.getEmail());

        Order other = orderRepository.saveAndFlush(Order.builder()
                .orderNumber("ORD-SCOPE-" + UUID.randomUUID().toString().substring(0, 8))
                .user(admin)
                .address(order.getAddress())
                .status(OrderStatus.DELIVERED)
                .paymentStatus(PaymentStatus.PAID)
                .paymentMethod(PaymentMethod.ONLINE)
                .totalAmount(new BigDecimal("650.00"))
                .shippingCost(BigDecimal.ZERO)
                .build());
        OrderItem otherLine = orderItemRepository.saveAndFlush(OrderItem.builder()
                .order(other)
                .productVariant(variant)
                .quantity(1)
                .unitPrice(new BigDecimal("650.00"))
                .totalPrice(new BigDecimal("650.00"))
                .build());
        other.getItems().add(otherLine);
        entityManager.flush();

        CreateReturnRequest otherRequest = new CreateReturnRequest();
        otherRequest.setReason("Not what I ordered");
        CreateReturnRequest.Item otherItem = new CreateReturnRequest.Item();
        otherItem.setOrderItemId(otherLine.getId());
        otherItem.setQuantity(1);
        otherRequest.setItems(List.of(otherItem));
        returnService.create(other.getId(), otherRequest, admin.getEmail());

        mockMvc.perform(get("/api/dashboard/orders/{id}/returns", order.getId())
                .with(user(principalOf(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].orderId").value(order.getId()))
                .andExpect(jsonPath("$.data[0].items[0].orderItemId").value(line.getId()));
    }

    @Test
    void theListNeedsOrderRead() throws Exception {
        mockMvc.perform(get("/api/dashboard/orders/{id}/returns", order.getId())
                .with(user("no-orders").authorities(new SimpleGrantedAuthority("product:read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatingAReturnNeedsOrderUpdate() throws Exception {
        mockMvc.perform(post("/api/dashboard/orders/{id}/returns", order.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(2))
                .with(user("read-only").authorities(new SimpleGrantedAuthority("order:read"))))
                .andExpect(status().isForbidden());

        // Scoped to THIS test's order: the table is shared, and a return committed
        // by another run (or a manual check) must not fail a refusal test.
        assertThat(returnRepository.findByOrderIdOrderByCreatedAtDescIdDesc(order.getId())).isEmpty();
    }

    @Test
    void approvingNeedsOrderUpdate() throws Exception {
        OrderReturnResponse created = returnService.create(order.getId(), request(1, null), admin.getEmail());

        mockMvc.perform(post("/api/dashboard/orders/{id}/returns/{returnId}/approve",
                order.getId(), created.getId())
                .with(user("read-only").authorities(new SimpleGrantedAuthority("order:read"))))
                .andExpect(status().isForbidden());

        assertThat(variantRepository.findStockById(variant.getId())).isEqualTo(7);
    }

    /** The over-quantity refusal reaches the client as a 400 with the number left. */
    @Test
    void theApiRefusesMoreUnitsThanTheLineCanReturn() throws Exception {
        mockMvc.perform(post("/api/dashboard/orders/{id}/returns", order.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(9))
                .with(user(principalOf(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("3")));
    }

    @Test
    void theApiRecordsAReasonlessReturnAs400() throws Exception {
        mockMvc.perform(post("/api/dashboard/orders/{id}/returns", order.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"\",\"items\":[{\"orderItemId\":" + line.getId() + ",\"quantity\":1}]}")
                .with(user(principalOf(admin))))
                .andExpect(status().isBadRequest());

        // Scoped to THIS test's order: the table is shared, and a return committed
        // by another run (or a manual check) must not fail a refusal test.
        assertThat(returnRepository.findByOrderIdOrderByCreatedAtDescIdDesc(order.getId())).isEmpty();
    }

    /** A return of ANOTHER order is a 404, not a wrong-order success. */
    @Test
    void aReturnOfAnotherOrderIsNotFound() throws Exception {
        OrderReturnResponse created = returnService.create(order.getId(), request(1, null), admin.getEmail());

        Order other = orderRepository.save(Order.builder()
                .orderNumber("ORD-WRONG-" + UUID.randomUUID().toString().substring(0, 8))
                .user(admin)
                .address(order.getAddress())
                .status(OrderStatus.DELIVERED)
                .paymentStatus(PaymentStatus.PAID)
                .paymentMethod(PaymentMethod.ONLINE)
                .totalAmount(new BigDecimal("1.00"))
                .shippingCost(BigDecimal.ZERO)
                .build());

        mockMvc.perform(post("/api/dashboard/orders/{id}/returns/{returnId}/approve", other.getId(),
                created.getId())
                .with(user(principalOf(admin))))
                .andExpect(status().isNotFound());

        assertThat(variantRepository.findStockById(variant.getId())).isEqualTo(7);
    }

    // ─── fixtures ────────────────────────────────────────────────────────────

    private CreateReturnRequest request(int quantity, String note) {
        CreateReturnRequest request = new CreateReturnRequest();
        request.setReason("The grind was wrong");
        CreateReturnRequest.Item item = new CreateReturnRequest.Item();
        item.setOrderItemId(line.getId());
        item.setQuantity(quantity);
        item.setNote(note);
        request.setItems(List.of(item));
        return request;
    }

    private String body(int quantity) {
        return "{\"reason\":\"The grind was wrong\",\"items\":[{\"orderItemId\":" + line.getId()
                + ",\"quantity\":" + quantity + "}]}";
    }

    private List<OrderStatusHistory> eventsOf(Long orderId) {
        return historyRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .filter(row -> row.getEventType() != null)
                .toList();
    }

    private UserPrincipal principalOf(User user) {
        return UserPrincipal.create(userRepository.findById(user.getId()).orElseThrow());
    }
}
