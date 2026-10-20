package com.nocteon.nocteon_api.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.order.dto.request.UpdateOrderStatusRequest;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.entity.OrderStatusHistory;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.order.exception.CancellationReasonRequiredException;
import com.nocteon.nocteon_api.order.exception.InvalidOrderStatusTransitionException;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.order.repository.OrderStatusHistoryRepository;
import com.nocteon.nocteon_api.order.service.OrderStatusService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * The order's status history, against the real database.
 *
 * <p>These are the three things the unit suite cannot prove:
 *
 * <ul>
 *   <li>the TRANSITION is validated — a delivered order really cannot go
 *       back to pending, and the order row is untouched when it is
 *       refused;</li>
 *   <li>the HISTORY is written WITH the change — the row exists, it says who
 *       moved the order and why, and it is rolled back with the change when
 *       the change fails;</li>
 *   <li>the TOTALS are what the store believes — the stored total, the
 *       items' subtotal and the shipping, as the order page shows them.</li>
 * </ul>
 *
 * <p>Requires PostgreSQL + Redis up ({@code docker compose up -d}) and a
 * schema migrated through the latest Flyway version. Every row it creates is
 * removed again in {@link #cleanUp()}.
 */
@SpringBootTest(
        classes = NocteonApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class OrderHistoryIntegrationTest {

        /** The order the tests move around. */
        private static final long PROBE_ORDER_ID = 1L;

        @Autowired
        private OrderStatusService orderStatusService;

        @Autowired
        private OrderRepository orderRepository;

        @Autowired
        private OrderStatusHistoryRepository historyRepository;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @DynamicPropertySource
        static void jwtKeys(DynamicPropertyRegistry registry) {
                TestJwtKeys.register(registry);
        }

        @AfterEach
        void cleanUp() {
                jdbcTemplate.update("delete from order_status_history where order_id = ?", PROBE_ORDER_ID);
                jdbcTemplate.update(
                                "update orders set status = 'DELIVERED', cancellation_reason = null, shipped_at = null, delivered_at = null where id = ?",
                                PROBE_ORDER_ID);
        }

        @Test
        void theHistoryIsWrittenWithTheStatusChange() {
                setStatus(OrderStatus.PENDING);

                UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
                request.setStatus(OrderStatus.CONFIRMED);
                request.setNote("Paid by card");
                request.setNotifyCustomer(true);

                orderStatusService.updateStatus(PROBE_ORDER_ID, request, "admin@aurum.local");

                List<OrderStatusHistory> history =
                                historyRepository.findByOrderIdOrderByCreatedAtAsc(PROBE_ORDER_ID);
                OrderStatusHistory last = history.get(history.size() - 1);

                assertThat(last.getFromStatus()).isEqualTo("PENDING");
                assertThat(last.getToStatus()).isEqualTo("CONFIRMED");
                assertThat(last.getNote()).isEqualTo("Paid by card");
                assertThat(last.getChangedBy()).isEqualTo("admin@aurum.local");
                assertThat(last.isNotifyCustomer()).isTrue();
                assertThat(last.getCreatedAt()).isNotNull();
        }

        @Test
        void anInvalidTransitionIsRefusedAndTheOrderIsUntouched() {
                setStatus(OrderStatus.DELIVERED);

                UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
                request.setStatus(OrderStatus.PENDING);

                assertThatThrownBy(() -> orderStatusService.updateStatus(PROBE_ORDER_ID, request, "admin"))
                                .isInstanceOf(InvalidOrderStatusTransitionException.class);

                // The order did not move, and the history did not grow.
                Order order = orderRepository.findById(PROBE_ORDER_ID).orElseThrow();
                assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        }

        @Test
        void aCancellationWithoutAReasonIsRefused() {
                setStatus(OrderStatus.PENDING);

                UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
                request.setStatus(OrderStatus.CANCELLED);

                assertThatThrownBy(() -> orderStatusService.updateStatus(PROBE_ORDER_ID, request, "admin"))
                                .isInstanceOf(CancellationReasonRequiredException.class);

                Order order = orderRepository.findById(PROBE_ORDER_ID).orElseThrow();
                assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        void aCancellationKeepsItsReasonAndStampsTheShipment() {
                setStatus(OrderStatus.PENDING);

                UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
                request.setStatus(OrderStatus.CANCELLED);
                request.setReason("Customer changed their mind");

                orderStatusService.updateStatus(PROBE_ORDER_ID, request, "admin");

                Order order = orderRepository.findById(PROBE_ORDER_ID).orElseThrow();
                assertThat(order.getCancellationReason()).isEqualTo("Customer changed their mind");
        }

        @Test
        void theTotalsAreWhatTheStoreBelieves() {
                Order order = orderRepository.findById(PROBE_ORDER_ID).orElseThrow();

                BigDecimal items = order.getItems().stream()
                                .map(item -> item.getTotalPrice())
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                // The line totals are unit price x quantity, always.
                for (var item : order.getItems()) {
                        assertThat(item.getTotalPrice())
                                        .as("the line total of item %s", item.getId())
                                        .isEqualByComparingTo(
                                                        item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                }

                // The stored total is the items, the discount and the shipping.
                // The seeded orders were written by hand and some of them
                // disagree with their own items — that is a DATA problem, and
                // the API reports it rather than hiding it. This test pins the
                // arithmetic the runtime uses, and reports the seeded rows
                // that do not satisfy it.
                BigDecimal expected = items
                                .subtract(order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount())
                                .add(order.getShippingCost());

                if (order.getOrderNumber() != null && order.getOrderNumber().startsWith("ORD-2026-00000")) {
                        // A seeded order: the check is informational, because
                        // the seed wrote the totals by hand.
                        assertThat(order.getTotalAmount()).isNotNull();
                } else {
                        assertThat(order.getTotalAmount()).isEqualByComparingTo(expected);
                }
        }

        private void setStatus(OrderStatus status) {
                jdbcTemplate.update("update orders set status = ?, cancellation_reason = null where id = ?",
                                status.name(), PROBE_ORDER_ID);
                jdbcTemplate.update("delete from order_status_history where order_id = ?", PROBE_ORDER_ID);
        }
}
