package com.nocteon.nocteon_api.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.address.entity.Address;
import com.nocteon.nocteon_api.address.repository.AddressRepository;
import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.enums.Provider;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.mail.service.BrevoClient;
import com.nocteon.nocteon_api.order.dto.request.UpdateOrderStatusRequest;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.order.service.OrderStatusService;
import com.nocteon.nocteon_api.payment.entity.Payment;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;
import com.nocteon.nocteon_api.payment.exception.RefundFailedException;
import com.nocteon.nocteon_api.payment.repository.PaymentRepository;
import com.nocteon.nocteon_api.payment.service.PaymentService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * The ORDER half of the audit log: the rows that answer "who moved this order,
 * who cancelled it, who gave the money back, who sent the confirmation again".
 *
 * <p>Before this, the enum had {@code ORDER_CANCEL} and {@code ORDER_STATUS_CHANGE}
 * and the order services wrote neither: the dashboard's audit page could only ever
 * show role and user changes.
 *
 * <p>What is pinned here, per action:
 *
 * <ul>
 *   <li>the row is written with the action, the entity, the acting admin (id AND
 *       e-mail) and ONLY the fields that changed;</li>
 *   <li>a REFUSED change writes no success row — a refused transition and a refund
 *       the gateway rejected both answer with a {@code success = false} row and the
 *       reason instead;</li>
 *   <li>a change that is rolled back after the write leaves no row at all: the
 *       success row shares the change's transaction.</li>
 * </ul>
 *
 * <p>Real database, real routes, no {@code @Transactional} on the class: the audit
 * writer commits with the request, and a test transaction would hide exactly the
 * behaviour under test. Every fixture is created for the test and deleted again.
 */
@SpringBootTest(classes = NocteonApiApplication.class)
@AutoConfigureMockMvc
class OrderAuditCoverageIntegrationTest {

    private static final BigDecimal ORDER_TOTAL = new BigDecimal("650.00");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private OrderStatusService orderStatusService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    /** The gateway is the one collaborator that is an HTTP call to someone else's server. */
    @MockitoBean
    private PaymentService paymentService;
    /** Same for the mail provider: the resend is audited, the send itself is not our code. */
    @MockitoBean
    private BrevoClient brevoClient;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    private String suffix;
    private User admin;
    private User customer;
    private Address address;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        deleteFixtures();

        admin = userRepository.save(User.builder()
                .username("auditcov-order-admin-" + suffix)
                .email("auditcov-order-admin-" + suffix + "@example.test")
                .password("x")
                .provider(Provider.LOCAL)
                .role("ADMIN")
                .enabled(true)
                .build());

        customer = userRepository.save(User.builder()
                .username("auditcov-order-customer-" + suffix)
                .email("auditcov-order-customer-" + suffix + "@example.test")
                .password("x")
                .provider(Provider.LOCAL)
                .role("CUSTOMER")
                .enabled(true)
                .build());

        address = addressRepository.save(Address.builder()
                .user(customer)
                .fullName("Audit Fixture")
                .phone("+201000000000")
                .street("12 Nile Street")
                .city("Cairo")
                .country("Egypt")
                .postalCode("11511")
                .isDefault(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        deleteFixtures();
    }

    // ── status change ────────────────────────────────────────────────────────

    @Test
    void aStatusChangeRecordsWhoMovedItAndFromWhatToWhat() throws Exception {
        Order order = saveOrder(OrderStatus.PENDING, PaymentStatus.PENDING);

        mockMvc.perform(asAdmin(put("/api/dashboard/orders/{id}/status", order.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\",\"note\":\"Paid by card\"}")))
                .andExpect(status().isOk());

        Map<String, Object> row = latestRow(AuditAction.ORDER_STATUS_CHANGE, order.getId());
        assertThat(row.get("user_id")).isEqualTo(admin.getId());
        assertThat(row.get("user_email")).isEqualTo(admin.getEmail());
        assertThat(row.get("entity_type")).isEqualTo("Order");
        assertThat(row.get("entity_slug")).isEqualTo(order.getOrderNumber());
        assertThat(row.get("success")).isEqualTo(true);

        // Only the status moved, and the note is metadata (it is the history row's
        // sentence, not a field of the order).
        assertThat(oldValues(row)).containsExactly(Map.entry("status", "PENDING"));
        assertThat(newValues(row)).containsEntry("status", "CONFIRMED");
        assertThat(newValues(row)).doesNotContainKey("cancellationReason");
        assertThat(metadata(row)).containsEntry("note", "Paid by card");
    }

    @Test
    void aCancellationIsItsOwnActionAndKeepsItsReason() throws Exception {
        Order order = saveOrder(OrderStatus.PENDING, PaymentStatus.PENDING);

        mockMvc.perform(asAdmin(put("/api/dashboard/orders/{id}/status", order.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\",\"reason\":\"Customer changed their mind\"}")))
                .andExpect(status().isOk());

        Map<String, Object> row = latestRow(AuditAction.ORDER_CANCEL, order.getId());
        assertThat(row.get("user_id")).isEqualTo(admin.getId());
        assertThat(row.get("user_email")).isEqualTo(admin.getEmail());
        assertThat(oldValues(row)).containsEntry("status", "PENDING");
        assertThat(newValues(row))
                .containsEntry("status", "CANCELLED")
                .containsEntry("cancellationReason", "Customer changed their mind");

        // And the generic action was NOT used: one change, one row.
        assertThat(countRows(AuditAction.ORDER_STATUS_CHANGE, order.getId())).isZero();
    }

    // ── refunds ──────────────────────────────────────────────────────────────

    @Test
    void aRefundRecordsTheAmountAndTheGatewayReference() throws Exception {
        Order order = saveOrder(OrderStatus.DELIVERED, PaymentStatus.PAID);
        savePayment(order);
        when(paymentService.refund(any(), any())).thenReturn("REF-AUDIT-1");

        mockMvc.perform(asAdmin(post("/api/dashboard/orders/{id}/refund", order.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50.00,\"reason\":\"The bag was damaged\"}")))
                .andExpect(status().isCreated());

        Map<String, Object> row = latestRow(AuditAction.ORDER_REFUND, order.getId());
        assertThat(row.get("user_id")).isEqualTo(admin.getId());
        assertThat(row.get("user_email")).isEqualTo(admin.getEmail());
        assertThat(row.get("success")).isEqualTo(true);
        assertThat(oldValues(row)).containsEntry("paymentStatus", "PAID");
        assertThat(newValues(row))
                .containsEntry("paymentStatus", "REFUNDED")
                .containsEntry("gatewayReference", "REF-AUDIT-1");
        assertThat(metadata(row)).containsEntry("reason", "The bag was damaged");

        // No card number, no token: the row carries the amount and the reference,
        // nothing that could be replayed.
        assertThat(String.valueOf(row.get("new_values"))).doesNotContain("card", "Card", "token", "pan");
    }

    @Test
    void aRefundTheGatewayRefusesWritesAFailureAndNoSuccessRow() throws Exception {
        Order order = saveOrder(OrderStatus.DELIVERED, PaymentStatus.PAID);
        savePayment(order);
        when(paymentService.refund(any(), any()))
                .thenThrow(new RefundFailedException("The gateway said no"));

        mockMvc.perform(asAdmin(post("/api/dashboard/orders/{id}/refund", order.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50.00,\"reason\":\"The bag was damaged\"}")))
                .andExpect(status().isBadGateway());

        // The money did not go back, so no success row claims it did.
        assertThat(countRows(AuditAction.ORDER_REFUND, order.getId())).isZero();
        assertThat(countRowsOfAnyStatus(AuditAction.ORDER_REFUND, order.getId())).isEqualTo(1);

        Map<String, Object> refusal = jdbcTemplate.queryForMap(
                "SELECT user_id, user_email, success, error_message FROM public.audit_logs "
                        + "WHERE action = ? AND entity_type = 'Order' AND entity_id = ? ORDER BY id DESC LIMIT 1",
                AuditAction.ORDER_REFUND.name(), order.getId());
        assertThat(refusal.get("success")).isEqualTo(false);
        assertThat(refusal.get("user_id")).isEqualTo(admin.getId());
        assertThat(String.valueOf(refusal.get("error_message")))
                .contains("gateway")
                .contains("The gateway said no");
    }

    // ── refusals and rollbacks ───────────────────────────────────────────────

    @Test
    void aRefusedTransitionWritesNoSuccessRowOnlyTheRefusal() throws Exception {
        Order order = saveOrder(OrderStatus.DELIVERED, PaymentStatus.PAID);

        mockMvc.perform(asAdmin(put("/api/dashboard/orders/{id}/status", order.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}")))
                .andExpect(status().isConflict());

        assertThat(countRows(AuditAction.ORDER_STATUS_CHANGE, order.getId())).isZero();
        Map<String, Object> refusal = jdbcTemplate.queryForMap(
                "SELECT success, error_message FROM public.audit_logs "
                        + "WHERE action = ? AND entity_type = 'Order' AND entity_id = ? ORDER BY id DESC LIMIT 1",
                AuditAction.ORDER_STATUS_CHANGE.name(), order.getId());
        assertThat(refusal.get("success")).isEqualTo(false);
        assertThat(String.valueOf(refusal.get("error_message")))
                .isEqualTo("The order cannot move from DELIVERED to CONFIRMED.");
    }

    /**
     * The success row shares the change's transaction.
     *
     * <p>This is the one property a row-per-action scheme can get wrong and nobody
     * notices: writing the audit row in its OWN transaction (or after the commit)
     * leaves a row behind when the change is rolled back, and the log then claims
     * an order moved when it did not. Here the outer transaction is rolled back on
     * purpose and the row must be gone with it.
     */
    @Test
    void aStatusChangeThatIsRolledBackLeavesNoSuccessRow() {
        Order order = saveOrder(OrderStatus.PENDING, PaymentStatus.PENDING);

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
            request.setStatus(OrderStatus.CONFIRMED);
            orderStatusService.updateStatus(order.getId(), request, admin.getEmail());
            // The change happened inside this transaction and the audit row with it...
            status.setRollbackOnly();
        });

        // ...and then it was rolled back: neither the status nor the row survives.
        assertThat(countRowsOfAnyStatus(AuditAction.ORDER_STATUS_CHANGE, order.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, order.getId()))
                .isEqualTo("PENDING");
    }

    // ── the confirmation resend ──────────────────────────────────────────────

    @Test
    void resendingTheConfirmationIsAuditedWithTheOutcome() throws Exception {
        Order order = saveOrder(OrderStatus.CONFIRMED, PaymentStatus.PAID);
        when(brevoClient.sendTransactionalEmail(any(), any(), any())).thenReturn("provider-message-id");

        mockMvc.perform(asAdmin(post("/api/dashboard/orders/{id}/confirmation-email", order.getId())))
                .andExpect(status().isOk());

        Map<String, Object> row = latestRow(AuditAction.ORDER_CONFIRMATION_RESEND, order.getId());
        assertThat(row.get("user_id")).isEqualTo(admin.getId());
        assertThat(row.get("user_email")).isEqualTo(admin.getEmail());
        assertThat(newValues(row))
                .containsEntry("recipientEmail", customer.getEmail())
                .containsEntry("emailStatus", "SENT");
        assertThat(metadata(row)).containsEntry("resendNumber", 1);
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private Order saveOrder(OrderStatus status, PaymentStatus paymentStatus) {
        return orderRepository.save(Order.builder()
                .orderNumber("ORD-AUDIT-" + suffix + "-" + status.name())
                .user(customer)
                .address(address)
                .status(status)
                .paymentStatus(paymentStatus)
                .totalAmount(ORDER_TOTAL)
                .build());
    }

    private Payment savePayment(Order order) {
        return paymentRepository.save(Payment.builder()
                .order(order)
                .amount(ORDER_TOTAL)
                .currency("EGP")
                .status(PaymentStatus.PAID)
                .providerPaymentId("TXN-AUDIT-" + suffix)
                .attemptNumber(1)
                .build());
    }

    /** An authenticated caller whose principal is a real {@link UserPrincipal} (what production sets). */
    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        User fresh = userRepository.findById(admin.getId()).orElseThrow();
        UserPrincipal principal = UserPrincipal.create(fresh);
        return request.with(authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())));
    }

    /** The newest row for this action and order, whatever its outcome. */
    private Map<String, Object> latestRow(AuditAction action, Long orderId) {
        return jdbcTemplate.queryForMap(
                "SELECT user_id, user_email, entity_type, entity_slug, old_values, new_values, metadata, success "
                        + "FROM public.audit_logs WHERE action = ? AND entity_type = 'Order' AND entity_id = ? "
                        + "AND success = true ORDER BY id DESC LIMIT 1",
                action.name(), orderId);
    }

    private int countRows(AuditAction action, Long orderId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.audit_logs WHERE action = ? AND entity_type = 'Order' "
                        + "AND entity_id = ? AND success = true",
                Integer.class, action.name(), orderId);
        return count == null ? 0 : count;
    }

    private int countRowsOfAnyStatus(AuditAction action, Long orderId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.audit_logs WHERE action = ? AND entity_type = 'Order' AND entity_id = ?",
                Integer.class, action.name(), orderId);
        return count == null ? 0 : count;
    }

    /**
     * The stored {@code old_values}, parsed back into a map.
     *
     * <p>Asserting on the JSON as text would pass for a blob the page cannot render;
     * the drawer reads this text as JSON, so the test does too.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> oldValues(Map<String, Object> row) {
        return readJson((String) row.get("old_values"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> newValues(Map<String, Object> row) {
        return readJson((String) row.get("new_values"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadata(Map<String, Object> row) {
        return readJson((String) row.get("metadata"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readJson(String json) {
        if (json == null) {
            return Map.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            throw new AssertionError("the audit row's JSON does not parse: " + json, e);
        }
    }

    /** Removes every row this class can write: the audit rows first, the fixtures last. */
    private void deleteFixtures() {
        List<String> emails = List.of(
                "auditcov-order-admin-" + suffix + "@example.test",
                "auditcov-order-customer-" + suffix + "@example.test");

        jdbcTemplate.update("DELETE FROM public.audit_logs WHERE user_email IN (?, ?)",
                emails.get(0), emails.get(1));
        jdbcTemplate.update(
                "DELETE FROM public.audit_logs WHERE entity_type = 'Order' AND entity_id IN "
                        + "(SELECT id FROM orders WHERE order_number LIKE 'ORD-AUDIT-%')");
        // The order children (items, history, notes, refunds, payments) hang off the
        // order with ON DELETE CASCADE; the address and the users go afterwards.
        jdbcTemplate.update("DELETE FROM orders WHERE user_id IN "
                + "(SELECT id FROM users WHERE username LIKE 'auditcov-order-%')");
        jdbcTemplate.update("DELETE FROM users WHERE username LIKE 'auditcov-order-%'");
    }
}
