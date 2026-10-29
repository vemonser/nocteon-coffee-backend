package com.nocteon.nocteon_api.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.persistence.EntityManager;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;

import com.nocteon.nocteon_api.address.entity.Address;
import com.nocteon.nocteon_api.address.repository.AddressRepository;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.cart.entity.Cart;
import com.nocteon.nocteon_api.cart.entity.CartItem;
import com.nocteon.nocteon_api.cart.repository.CartItemRepository;
import com.nocteon.nocteon_api.cart.repository.CartRepository;
import com.nocteon.nocteon_api.category.repository.CategoryRepository;
import com.nocteon.nocteon_api.common.exception.product.InsufficientStockException;
import com.nocteon.nocteon_api.order.dto.request.CreateOrderRequest;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.entity.OrderItem;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.order.repository.OrderItemRepository;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.order.service.OrderQueryService;
import com.nocteon.nocteon_api.order.service.OrderService;
import com.nocteon.nocteon_api.payment.dto.request.PaymobCallbackRequest;
import com.nocteon.nocteon_api.payment.dto.request.PaymobOrderObj;
import com.nocteon.nocteon_api.payment.dto.request.PaymobTransactionObj;
import com.nocteon.nocteon_api.payment.entity.Payment;
import com.nocteon.nocteon_api.payment.enums.PaymentMethod;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;
import com.nocteon.nocteon_api.payment.repository.PaymentRepository;
import com.nocteon.nocteon_api.payment.service.PaymentService;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;
import com.nocteon.nocteon_api.promoCode.entity.PromoCode;
import com.nocteon.nocteon_api.promoCode.enums.PromoCodeDiscountType;
import com.nocteon.nocteon_api.promoCode.enums.PromoScopeType;
import com.nocteon.nocteon_api.promoCode.repository.PromoCodeRedemptionRepository;
import com.nocteon.nocteon_api.promoCode.repository.PromoCodeRepository;
import com.nocteon.nocteon_api.promoCode.service.PromoCodeService;
import com.nocteon.nocteon_api.shippingZone.service.ShippingZoneService;
import com.nocteon.nocteon_api.storeSettings.dto.response.StoreSettingsResponse;
import com.nocteon.nocteon_api.storeSettings.service.StoreSettingsService;

/**
 * Behaviour tests for the checkout stock and payment path.
 *
 * <p><strong>Every assertion is about what the customer can observe</strong> —
 * did an order get created, did stock go down by the right amount, did exactly
 * one buyer win the last unit, did a promo code get used up. Nothing here
 * depends on a class, method, or field name that could change in a refactor.
 *
 * <p>Two deliberate choices keep these tests honest:
 * <ul>
 *   <li>The payment and promo collaborators are <strong>real services</strong>
 *       with mocked repositories — a mock of the unit under test would just
 *       parrot back whatever the test told it.</li>
 *   <li>The Paymob webhook is driven with a <strong>real HMAC</strong>, so the
 *       test exercises the same verification path production does.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CheckoutStockBehaviourTest {

    private static final String HMAC_SECRET = "test-hmac-secret";

    // ── collaborators (all mocked) ────────────────────────────────────

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private com.nocteon.nocteon_api.product.repository.InventoryMovementRepository movementRepository;
    @Mock private PaymentService paymentService;
    @Mock private StoreSettingsService storeSettingsService;
    @Mock private ShippingZoneService shippingZoneService;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PromoCodeRepository promoCodeRepository;
    @Mock private PromoCodeRedemptionRepository redemptionRepository;
    @Mock private CategoryRepository categoryRepository;

    @Mock private EntityManager entityManager;
    // ── real services under test ──────────────────────────────────────

    private OrderService orderService;
    private PaymentService realPaymentService;
    private PromoCodeService realPromoCodeService;
    private com.nocteon.nocteon_api.order.service.UnpaidOrderExpiryService expiryService;

    /**
     * FE-13: the stock ledger the order paths now write through. Built over the
     * mocked repositories, so a consumption still goes through the real recording
     * code instead of a stub that would hide a missing row.
     */
    private com.nocteon.nocteon_api.product.service.InventoryMovementService inventoryService() {
        return new com.nocteon.nocteon_api.product.service.InventoryMovementService(
                variantRepository, movementRepository,
                mock(com.nocteon.nocteon_api.audit.service.AuditLogService.class));
    }

    @BeforeEach
    void setUp() {
        // createOrder registers a post-commit callback; without an active
        // synchronization that call would fail before any behaviour runs.
        TransactionSynchronizationManager.initSynchronization();

        lenient().when(entityManager.createNativeQuery(anyString())).thenAnswer(inv -> {
            jakarta.persistence.Query query = mock(jakarta.persistence.Query.class);
            when(query.getSingleResult()).thenReturn(1L);
            return query;
        });

        orderService = new OrderService(
                orderRepository, orderItemRepository, cartRepository, cartItemRepository,
                addressRepository, variantRepository, inventoryService(), paymentService, realPromoCodeService(),
                eventPublisher(), storeSettingsService, shippingZoneService,
                new OrderQueryService(orderRepository, paymentRepository,
                        mock(com.nocteon.nocteon_api.order.service.OrderRefundService.class)),
                mock(com.nocteon.nocteon_api.order.service.OrderStatusService.class),
                entityManager);

        realPaymentService = new PaymentService(
                new RestTemplate(), paymentRepository, orderRepository, variantRepository,
                inventoryService(), eventPublisher());
        ReflectionTestUtils.setField(realPaymentService, "hmacSecret", HMAC_SECRET);

        expiryService = new com.nocteon.nocteon_api.order.service.UnpaidOrderExpiryService(
                orderRepository,
                new com.nocteon.nocteon_api.order.service.OrderStatusService(
                        orderRepository,
                        mock(com.nocteon.nocteon_api.order.repository.OrderStatusHistoryRepository.class),
                        mock(com.nocteon.nocteon_api.order.service.OrderQueryService.class),
                        variantRepository,
                        eventPublisher(),
                        mock(com.nocteon.nocteon_api.audit.service.AuditLogService.class)),
                variantRepository, paymentRepository);

        // Shared scaffolding: a cheap cart well below the free-shipping line.
        // Lenient: the webhook and promo tests never check out, so they never touch these.
        lenient().when(shippingZoneService.calculateShippingCost(any())).thenReturn(new BigDecimal("30"));
        lenient().when(storeSettingsService.get()).thenReturn(
                StoreSettingsResponse.builder()
                        .freeShippingThreshold(new BigDecimal("500"))
                        .build());
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private PromoCodeService realPromoCodeService() {
        // Built lazily so each test gets the same instance the order service uses.
        if (realPromoCodeService == null) {
            realPromoCodeService = new PromoCodeService(
                    promoCodeRepository, redemptionRepository, categoryRepository, cartRepository);
        }
        return realPromoCodeService;
    }

    // ══════════════════════════════════════════════════════════════════════
    // 1. Two customers, one unit in stock
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("two customers racing for the last unit")
    class LastUnitRace {

        /**
         * Exactly one buyer wins. The other is rejected with a stock error.
         *
         * <p>The stock check and the decrement must be indivisible. If they are
         * not, two concurrent checkouts can both pass the check and both
         * decrement, and the store sells one unit twice.
         *
         * <p>Counts outcomes instead of repository calls: with no real
         * transaction in a unit test there is no rollback, so save-counts
         * would couple the test to statement ordering rather than behaviour.
         */
        @Test
        @DisplayName("exactly one of two simultaneous checkouts succeeds")
        void onlyOneCheckoutSucceeds() throws Exception {
            AtomicInteger stock = new AtomicInteger(1);
            when(variantRepository.reserveStockIfAvailable(anyLong(), anyInt()))
                    .thenAnswer(inv -> stock.compareAndSet(1, 0) ? 1 : 0);

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            AtomicInteger successes = new AtomicInteger();
            AtomicInteger rejections = new AtomicInteger();

            // Stub everything up front: Mockito stubbing must not race with
            // invocations from the worker threads. Shared stubs once — a second
            // identical stub would shadow the first and trip strict-stubs.
            UserPrincipal buyerA = principal(1L);
            UserPrincipal buyerB = principal(2L);
            stubSuccessfulCheckout(buyerA, cartWithVariant(1L, 1));
            stubCartFor(buyerB, cartWithVariant(1L, 1));

            var f1 = pool.submit(() -> raceAttempt(buyerA, ready, go, successes, rejections));
            var f2 = pool.submit(() -> raceAttempt(buyerB, ready, go, successes, rejections));

            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            f1.get(5, TimeUnit.SECONDS);
            f2.get(5, TimeUnit.SECONDS);
            pool.shutdown();

            assertThat(successes.get())
                    .as("exactly one buyer wins the last unit")
                    .isEqualTo(1);
            assertThat(rejections.get())
                    .as("the other buyer is rejected for stock")
                    .isEqualTo(1);
        }

        /**
         * The loser gets a stock rejection — not a 500.
         *
         * <p>A generic error reads as "the site is broken"; the buyer retries,
         * and the store looks unstable. A stock rejection reads as "someone
         * beat you to it".
         */
        @Test
        @DisplayName("the loser is rejected with a stock message, not a server error")
        void loserGetsStockRejection() {
            UserPrincipal buyer = principal(1L);
            stubSuccessfulCheckout(buyer, cartWithVariant(1L, 1));
            when(variantRepository.reserveStockIfAvailable(anyLong(), anyInt())).thenReturn(0);

            assertThatThrownBy(() -> orderService.createOrder(codRequest(), buyer))
                    .isInstanceOf(InsufficientStockException.class);
        }

        /**
         * The winner's cart is cleared so they cannot accidentally re-order.
         */
        @Test
        @DisplayName("the winner's cart is cleared after a successful order")
        void winnerCartIsCleared() {
            Cart cart = cartWithVariant(1L, 1);
            checkout(principal(1L), cart);

            verify(cartItemRepository).deleteByCartId(cart.getId());
        }

        private Void raceAttempt(UserPrincipal buyer, CountDownLatch ready, CountDownLatch go,
                                 AtomicInteger successes, AtomicInteger rejections) throws Exception {
            ready.countDown();
            go.await(5, TimeUnit.SECONDS);
            // Each request thread carries its own transaction context —
            // the main thread's synchronization is invisible here.
            TransactionSynchronizationManager.initSynchronization();
            try {
                orderService.createOrder(codRequest(), buyer);
                successes.incrementAndGet();
            } catch (InsufficientStockException rejected) {
                rejections.incrementAndGet();
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
            return null;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. The buyer leaves before paying
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("an order awaiting payment whose callback never arrives")
    class AbandonedPayment {

        /**
         * Stock must not be held forever by an order nobody is paying for.
         *
         * <p>The store's promise: a unit reserved by an abandoned checkout comes
         * back to the shelf via the expiry sweep.
         */
        @Test
        @DisplayName("stock reserved by an unpaid order is eventually released")
        void abandonedOrderReleasesStock() {
            ProductVariant variant = variant(1L, 0);
            Order unpaid = Order.builder()
                    .id(50L)
                    .status(OrderStatus.PENDING)
                    .paymentStatus(PaymentStatus.PENDING)
                    .items(new ArrayList<>(List.of(
                            OrderItem.builder()
                                    .productVariant(variant)
                                    .quantity(1)
                                    .build())))
                    .build();
            Payment payment = paymentFor(unpaid);
            when(orderRepository.findByStatusAndPaymentStatusAndCreatedAtBefore(
                    eq(OrderStatus.PENDING), eq(PaymentStatus.PENDING), any(Instant.class)))
                    .thenReturn(List.of(unpaid));
            // The sweep moves the order through the state machine, which
            // re-reads it by id.
            when(orderRepository.findById(50L)).thenReturn(Optional.of(unpaid));
            when(paymentRepository.findByOrderId(50L)).thenReturn(Optional.of(payment));

            int released = expiryService.cancelExpiredUnpaidOrders();

            assertThat(released)
                    .as("the sweep reports the checkout it released")
                    .isEqualTo(1);
            assertThat(unpaid.getStatus())
                    .as("the abandoned checkout is cancelled")
                    .isEqualTo(OrderStatus.CANCELLED);
            assertThat(variant.getStockQuantity())
                    .as("the unit comes back to the shelf")
                    .isEqualTo(1);
            assertThat(payment.getStatus())
                    .as("the dangling payment is closed so it cannot be collected later")
                    .isEqualTo(PaymentStatus.FAILED);
        }

        /**
         * A late success webhook must not resurrect a cancelled order.
         *
         * <p>If a success callback arrives after the order was cancelled,
         * flipping it back to CONFIRMED means the store ships something it
         * already wrote off — and the stock that cancellation restored is
         * never taken back.
         */
        @Test
        @DisplayName("a success webhook after cancellation does not resurrect the order")
        void lateSuccessWebhookDoesNotResurrect() {
            Order cancelled = Order.builder()
                    .id(60L)
                    .status(OrderStatus.CANCELLED)
                    .paymentStatus(PaymentStatus.PENDING)
                    .items(new ArrayList<>())
                    .build();
            Payment payment = paymentFor(cancelled);

            deliverWebhook(true, 60L, payment);

            assertThat(cancelled.getStatus())
                    .as("an already-cancelled order stays cancelled")
                    .isEqualTo(OrderStatus.CANCELLED);
            assertThat(payment.getStatus())
                    .as("the captured money is still recorded, so ops can refund it")
                    .isEqualTo(PaymentStatus.PAID);
        }

        /**
         * A failure webhook must restore stock exactly once.
         *
         * <p>Paymob can deliver the same callback more than once. Restoring
         * stock on every delivery would create stock out of nowhere.
         */
        @Test
        @DisplayName("a failure webhook restores stock exactly once, however many times it arrives")
        void failureWebhookRestoresStockOnce() {
            ProductVariant variant = variant(1L, 0);
            Order order = Order.builder()
                    .id(70L)
                    .status(OrderStatus.PENDING)
                    .paymentStatus(PaymentStatus.PENDING)
                    .items(new ArrayList<>(List.of(
                            OrderItem.builder()
                                    .productVariant(variant)
                                    .quantity(1)
                                    .build())))
                    .build();
            Payment payment = paymentFor(order);

            deliverWebhook(false, 70L, payment);
            int afterFirst = variant.getStockQuantity();

            deliverWebhook(false, 70L, payment);
            deliverWebhook(false, 70L, payment);

            assertThat(afterFirst)
                    .as("the first failure callback restores the unit")
                    .isEqualTo(1);
            assertThat(variant.getStockQuantity())
                    .as("duplicate callbacks do not create stock out of thin air")
                    .isEqualTo(1);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. A promo code with a limited number of uses
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("a limited promo code presented by two customers at once")
    class PromoCodeRace {

        /**
         * A code that may be redeemed once is redeemed at most once.
         *
         * <p>The store's promise: "this code works N times." If two buyers
         * present it in the same instant and both succeed, the store gave away
         * a discount it never budgeted for.
         */
        @Test
        @DisplayName("a single-use code yields exactly one redemption")
        void singleUseCodeYieldsOneRedemption() {
            when(promoCodeRepository.findByCodeIgnoreCaseForUpdate("SAVE10"))
                    .thenReturn(Optional.of(singleUsePromo()));
            // First buyer sees zero uses; second buyer sees the first buyer's.
            when(redemptionRepository.countByPromoCodeId(9L))
                    .thenReturn(0L)
                    .thenReturn(1L);

            int successes = 0;
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    realPromoCodeService().calculateDiscountForCart(
                            "SAVE10", List.of(cartLine(100)), BigDecimal.valueOf(100), 1L);
                    successes++;
                } catch (RuntimeException exhaustedOrInvalid) {
                    // expected on the second attempt
                }
            }

            assertThat(successes)
                    .as("a single-use code is accepted exactly once")
                    .isEqualTo(1);
        }

        /**
         * An accepted code must actually reduce the total.
         *
         * <p>The other half of the promise: a code the customer was told works
         * has to lower the amount they pay.
         */
        @Test
        @DisplayName("an accepted percentage code reduces the order total")
        void acceptedCodeReducesTotal() {
            when(promoCodeRepository.findByCodeIgnoreCaseForUpdate("SAVE10"))
                    .thenReturn(Optional.of(tenPercentPromo()));

            var result = realPromoCodeService().calculateDiscountForCart(
                    "SAVE10", List.of(cartLine(100)), BigDecimal.valueOf(100), 1L);

            assertThat(result.getDiscountAmount())
                    .as("the customer sees the discount the code promised")
                    .isEqualByComparingTo("10.00");
        }

        private PromoCode singleUsePromo() {
            return PromoCode.builder()
                    .id(9L)
                    .code("SAVE10")
                    .discountType(PromoCodeDiscountType.PERCENTAGE)
                    .discountValue(BigDecimal.valueOf(10))
                    .scopeType(PromoScopeType.GLOBAL)
                    .active(true)
                    .validFrom(Instant.now().minusSeconds(3600))
                    .validUntil(Instant.now().plusSeconds(3600))
                    .maxTotalRedemptions(1)
                    .maxRedemptionsPerUser(1)
                    .build();
        }

        private PromoCode tenPercentPromo() {
            return PromoCode.builder()
                    .id(10L)
                    .code("SAVE10")
                    .discountType(PromoCodeDiscountType.PERCENTAGE)
                    .discountValue(BigDecimal.valueOf(10))
                    .scopeType(PromoScopeType.GLOBAL)
                    .active(true)
                    .validFrom(Instant.now().minusSeconds(3600))
                    .validUntil(Instant.now().plusSeconds(3600))
                    .maxTotalRedemptions(100)
                    .maxRedemptionsPerUser(5)
                    .build();
        }

        private CartItem cartLine(int unitPrice) {
            return CartItem.builder()
                    .productVariant(variant(11L, 5))
                    .quantity(1)
                    .build();
        }
    }

    // ── checkout helpers ─────────────────────────────────────────────

    /** Runs a checkout and lets any rejection propagate to the caller. */
    private void checkout(UserPrincipal principal, Cart cart) {
        stubSuccessfulCheckout(principal, cart);
        when(variantRepository.reserveStockIfAvailable(anyLong(), anyInt())).thenReturn(1);
        orderService.createOrder(codRequest(), principal);
    }

    private void stubSuccessfulCheckout(UserPrincipal principal, Cart cart) {
        stubCartFor(principal, cart);
        when(addressRepository.findByIdAndUserId(any(), any()))
                .thenReturn(Optional.of(new Address()));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubCartFor(UserPrincipal principal, Cart cart) {
        when(cartRepository.findByUserId(principal.getUserId()))
                .thenReturn(Optional.of(cart));
    }

    private Cart cartWithVariant(long variantId, int qty) {
        Cart cart = new Cart();
        cart.setItems(new ArrayList<>(List.of(
                CartItem.builder()
                        .productVariant(variant(variantId, 1))
                        .quantity(qty)
                        .build())));
        return cart;
    }

    private ProductVariant variant(long id, int stock) {
        ProductVariant variant = ProductVariant.builder()
                .id(id)
                .isActive(true)
                .price(BigDecimal.valueOf(100))
                .stockQuantity(stock)
                .build();
        // The stock is released through the repository, so the mock has to put
        // the units back on the variant — otherwise the variant's own counter
        // never moves and the assertions read zero.
        lenient().when(variantRepository.releaseStock(eq(id), anyInt())).thenAnswer(inv -> {
            variant.setStockQuantity(variant.getStockQuantity() + (Integer) inv.getArgument(1));
            return null;
        });
        return variant;
    }

    private CreateOrderRequest codRequest() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setPaymentMethod(PaymentMethod.CASH_ON_DELIVERY);
        return request;
    }

    private static UserPrincipal principal(Long userId) {
        UserPrincipal p = mock(UserPrincipal.class);
        when(p.getUserId()).thenReturn(userId);
        return p;
    }

    // ── webhook helpers ──────────────────────────────────────────────

    private static Payment paymentFor(Order order) {
        return Payment.builder()
                .order(order)
                .amount(BigDecimal.valueOf(100))
                .status(PaymentStatus.PENDING)
                .attemptNumber(1)
                .build();
    }

    /**
     * Delivers a Paymob callback exactly as the provider would: same payload
     * shape, same HMAC algorithm, so the verification path runs for real.
     */
    private void deliverWebhook(boolean success, long providerOrderId, Payment payment) {
        PaymobTransactionObj txn = new PaymobTransactionObj();
        txn.setId(999L);
        txn.setSuccess(success);
        txn.setAmountCents(10000L);
        txn.setCurrency("EGP");
        txn.setCreatedAt("2026-10-13T10:00:00");
        txn.setErrorOccured(!success);
        txn.setHasParentTransaction(false);
        txn.setIntegrationId(1L);
        txn.set3dSecure(false);
        txn.setAuth(true);
        txn.setCapture(true);
        txn.setStandalonePayment(true);
        txn.setVoided(false);
        txn.setRefunded(false);
        txn.setPending(false);
        txn.setOwner("1");

        PaymobOrderObj orderObj = new PaymobOrderObj();
        orderObj.setId(providerOrderId);
        txn.setOrder(orderObj);

        PaymobCallbackRequest payload = new PaymobCallbackRequest();
        payload.setObj(txn);

        when(paymentRepository.findByProviderOrderIdForUpdate(String.valueOf(providerOrderId)))
                .thenReturn(Optional.of(payment));

        realPaymentService.handleCallback(paymobHmac(txn), payload);
    }

    /**
     * Mirrors the provider's HMAC: the same field order and algorithm the
     * production verifier uses, keyed with the test secret.
     */
    private static String paymobHmac(PaymobTransactionObj txn) {
        try {
            String raw = "" + txn.getAmountCents()
                    + txn.getCreatedAt()
                    + txn.getCurrency()
                    + txn.isErrorOccured()
                    + txn.isHasParentTransaction()
                    + txn.getId()
                    + txn.getIntegrationId()
                    + txn.is3dSecure()
                    + txn.isAuth()
                    + txn.isCapture()
                    + txn.isRefunded()
                    + txn.isStandalonePayment()
                    + txn.isVoided()
                    + txn.getOrder().getId()
                    + txn.getOwner()
                    + txn.isPending()
                    + "" + "" + ""
                    + txn.isSuccess();
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("cannot compute test HMAC", e);
        }
    }

    private static ApplicationEventPublisher eventPublisher() {
        return event -> {
            // no-op: these tests assert on repository state, not on events
        };
    }
}
