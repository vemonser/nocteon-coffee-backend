package com.nocteon.nocteon_api.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.address.entity.Address;
import com.nocteon.nocteon_api.address.repository.AddressRepository;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.enums.Provider;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * FE-18b: one bulk status call, against the real database.
 *
 * <p>The unit suite can prove the aggregation and the wording; only a real
 * database proves the two things this endpoint exists for:
 *
 * <ul>
 *   <li>a REFUSED row does not take a successful row down with it — the
 *       successful row's status and history row are committed, the refused
 *       row's are untouched, and one call reports both;</li>
 *   <li>a bad body fails the CALL (400) while a bad ORDER fails only its ROW
 *       (200 with {@code success=false}), which is the difference between a
 *       client bug and an order that cannot move.</li>
 * </ul>
 *
 * <p>The class is deliberately NOT {@code @Transactional}: each row commits in
 * its own {@code REQUIRES_NEW} transaction, and a test that rolls everything
 * back at the end could not tell that apart from one shared transaction. Every
 * row the test creates is deleted again in {@link #cleanUp()}.
 */
@SpringBootTest(classes = NocteonApiApplication.class)
@AutoConfigureMockMvc
class BulkOrderStatusIntegrationTest {

        private static final String PATH = "/api/dashboard/orders/bulk-status";

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private UserRepository userRepository;
        @Autowired
        private AddressRepository addressRepository;
        @Autowired
        private OrderRepository orderRepository;
        @Autowired
        private JdbcTemplate jdbcTemplate;

        @DynamicPropertySource
        static void jwtKeys(DynamicPropertyRegistry registry) {
                TestJwtKeys.register(registry);
        }

        private User admin;
        /** PENDING: the row that can move to CONFIRMED. */
        private Order pending;
        /** CONFIRMED: the row that cannot move to CONFIRMED. */
        private Order confirmed;
        /** DELIVERED: a terminal row nobody can move. */
        private Order delivered;

        @BeforeEach
        void setUp() {
                String suffix = UUID.randomUUID().toString().substring(0, 8);

                admin = userRepository.save(User.builder()
                                .username("bulk-admin-" + suffix)
                                .email("bulk-admin-" + suffix + "@example.test")
                                .password("x")
                                .provider(Provider.LOCAL)
                                .role("ADMIN")
                                .enabled(true)
                                .build());

                User customer = userRepository.save(User.builder()
                                .username("bulk-customer-" + suffix)
                                .email("bulk-customer-" + suffix + "@example.test")
                                .password("x")
                                .provider(Provider.LOCAL)
                                .role("CUSTOMER")
                                .enabled(true)
                                .build());

                Address address = addressRepository.save(Address.builder()
                                .user(customer)
                                .fullName("Sara Ahmed")
                                .phone("+201000000000")
                                .street("12 Nile Street")
                                .city("Cairo")
                                .country("Egypt")
                                .postalCode("11511")
                                .isDefault(true)
                                .build());

                pending = saveOrder(suffix, 1, customer, address, OrderStatus.PENDING);
                confirmed = saveOrder(suffix, 2, customer, address, OrderStatus.CONFIRMED);
                delivered = saveOrder(suffix, 3, customer, address, OrderStatus.DELIVERED);
        }

        @AfterEach
        void cleanUp() {
                List<Long> orderIds = List.of(pending.getId(), confirmed.getId(), delivered.getId());
                // The children (items, history, notes, refunds, payments) hang
                // off the order with ON DELETE CASCADE.
                jdbcTemplate.update("delete from orders where id in (?, ?, ?)",
                                orderIds.get(0), orderIds.get(1), orderIds.get(2));
                // The address hangs off the customer.
                jdbcTemplate.update("delete from users where username like 'bulk-%'");
        }

        // ─── the mixed batch ─────────────────────────────────────────────────

        /**
         * One success, one refused, one unknown: ONE call, three outcomes, and
         * the database holds exactly what the answer says it holds.
         */
        @Test
        void aMixedBatchReportsEachRowAndCommitsOnlyTheRowsItCouldMove() throws Exception {
                mockMvc.perform(put(PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(List.of(pending.getId(), confirmed.getId(), 999_999_999L),
                                                "CONFIRMED", "Picked in the warehouse"))
                                .with(user(principalOf(admin))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.length()").value(3))
                                .andExpect(jsonPath("$.data[0].id").value(pending.getId()))
                                .andExpect(jsonPath("$.data[0].orderNumber").value(pending.getOrderNumber()))
                                .andExpect(jsonPath("$.data[0].success").value(true))
                                .andExpect(jsonPath("$.data[1].id").value(confirmed.getId()))
                                .andExpect(jsonPath("$.data[1].success").value(false))
                                .andExpect(jsonPath("$.data[1].message")
                                                .value(org.hamcrest.Matchers.containsString("CONFIRMED")))
                                .andExpect(jsonPath("$.data[2].id").value(999_999_999L))
                                .andExpect(jsonPath("$.data[2].success").value(false))
                                .andExpect(jsonPath("$.data[2].orderNumber").doesNotExist());

                // The successful row really moved, and its history explains it.
                assertThat(statusOf(pending)).isEqualTo("CONFIRMED");
                assertThat(historyCountOf(pending)).isEqualTo(1);
                assertThat(jdbcTemplate.queryForObject(
                                "select changed_by from order_status_history where order_id = ?",
                                String.class, pending.getId()))
                                .isEqualTo(admin.getEmail());

                // The refused row did not move, and nothing was written for it.
                assertThat(statusOf(confirmed)).isEqualTo("CONFIRMED");
                assertThat(historyCountOf(confirmed)).isZero();
        }

        /**
         * The bulk path never mails the customer: there is no per-order note to
         * quote, and the flag is not part of the body.
         */
        @Test
        void theHistoryRowSaysTheCustomerWasNotNotified() throws Exception {
                mockMvc.perform(put(PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(List.of(pending.getId()), "CONFIRMED", null))
                                .with(user(principalOf(admin))))
                                .andExpect(status().isOk());

                assertThat(jdbcTemplate.queryForObject(
                                "select notify_customer from order_status_history where order_id = ?",
                                Boolean.class, pending.getId()))
                                .isFalse();
                assertThat(jdbcTemplate.queryForObject(
                                "select note from order_status_history where order_id = ?",
                                String.class, pending.getId()))
                                .isNull();
        }

        /** A status nobody can reach is a per-row refusal, not a 409 for the batch. */
        @Test
        void aStatusNobodyCanReachIsRefusedPerRow() throws Exception {
                mockMvc.perform(put(PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(List.of(delivered.getId(), pending.getId()), "CONFIRMED", null))
                                .with(user(principalOf(admin))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data[0].success").value(false))
                                .andExpect(jsonPath("$.data[1].success").value(true));

                assertThat(statusOf(delivered)).isEqualTo("DELIVERED");
                assertThat(statusOf(pending)).isEqualTo("CONFIRMED");
        }

        /** An id that is not an order answers a row, not a 404 for the call. */
        @Test
        void anUnknownIdIsARowFailureAndNotANotFound() throws Exception {
                mockMvc.perform(put(PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(List.of(999_999_999L), "CONFIRMED", null))
                                .with(user(principalOf(admin))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.length()").value(1))
                                .andExpect(jsonPath("$.data[0].id").value(999_999_999L))
                                .andExpect(jsonPath("$.data[0].success").value(false))
                                .andExpect(jsonPath("$.data[0].message")
                                                .value(org.hamcrest.Matchers.containsString("not found")));
        }

        /** The same row twice is one attempt: the batch does not move it twice. */
        @Test
        void duplicateIdsCollapse() throws Exception {
                mockMvc.perform(put(PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(List.of(pending.getId(), pending.getId()), "CONFIRMED", null))
                                .with(user(principalOf(admin))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.length()").value(1))
                                .andExpect(jsonPath("$.data[0].success").value(true));

                assertThat(statusOf(pending)).isEqualTo("CONFIRMED");
                assertThat(historyCountOf(pending)).isEqualTo(1);
        }

        // ─── the body ────────────────────────────────────────────────────────

        @Test
        void anEmptyBodyIsRejectedWithA400() throws Exception {
                mockMvc.perform(put(PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                                .with(user(principalOf(admin))))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        void anEmptyIdListIsRejectedWithA400() throws Exception {
                mockMvc.perform(put(PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(List.of(), "CONFIRMED", null))
                                .with(user(principalOf(admin))))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errors[*].message", org.hamcrest.Matchers.hasItem(
                                                org.hamcrest.Matchers.containsString("at least one order"))));
        }

        @Test
        void moreThanOneHundredIdsAreRejectedWithA400AndNothingMoves() throws Exception {
                List<Long> ids = new ArrayList<>();
                ids.add(pending.getId());
                for (int index = 0; index < 100; index++) {
                        ids.add(999_999_999L - index);
                }
                assertThat(ids).hasSize(101);

                mockMvc.perform(put(PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(ids, "CONFIRMED", null))
                                .with(user(principalOf(admin))))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errors[*].message", org.hamcrest.Matchers.hasItem(
                                                org.hamcrest.Matchers.containsString("at most 100"))));

                // Validation failed before the loop: no row was touched.
                assertThat(statusOf(pending)).isEqualTo("PENDING");
                assertThat(historyCountOf(pending)).isZero();
        }

        // ─── access ──────────────────────────────────────────────────────────

        /** Holding some other permission is not holding this one. */
        @Test
        void theBulkEndpointNeedsTheOrderUpdatePermission() throws Exception {
                mockMvc.perform(put(PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(List.of(pending.getId()), "CONFIRMED", null))
                                .with(user("a-moderator-without-orders")
                                                .authorities(new SimpleGrantedAuthority("order:read"))))
                                .andExpect(status().isForbidden());

                assertThat(statusOf(pending)).isEqualTo("PENDING");
        }

        // ─── fixtures ────────────────────────────────────────────────────────

        private Order saveOrder(
                        String suffix, int index, User customer, Address address, OrderStatus status) {

                return orderRepository.save(Order.builder()
                                .orderNumber("ORD-BULK-" + suffix + "-" + index)
                                .user(customer)
                                .address(address)
                                .status(status)
                                .totalAmount(new BigDecimal("650.00"))
                                .paymentStatus(PaymentStatus.PENDING)
                                .build());
        }

        private String statusOf(Order order) {
                return jdbcTemplate.queryForObject(
                                "select status from orders where id = ?", String.class, order.getId());
        }

        private int historyCountOf(Order order) {
                Integer count = jdbcTemplate.queryForObject(
                                "select count(*) from order_status_history where order_id = ?",
                                Integer.class, order.getId());
                return count == null ? 0 : count;
        }

        private String body(List<Long> ids, String status, String note) {
                StringBuilder json = new StringBuilder("{\"ids\":[");
                for (int index = 0; index < ids.size(); index++) {
                        if (index > 0) {
                                json.append(',');
                        }
                        json.append(ids.get(index));
                }
                json.append("],\"status\":\"").append(status).append('"');
                if (note != null) {
                        json.append(",\"note\":\"").append(note).append('"');
                }
                return json.append('}').toString();
        }

        /** The principal the security filter chain would build from this user. */
        private UserPrincipal principalOf(User user) {
                return UserPrincipal.create(userRepository.findById(user.getId()).orElseThrow());
        }
}
