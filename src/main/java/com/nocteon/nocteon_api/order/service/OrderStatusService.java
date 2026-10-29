package com.nocteon.nocteon_api.order.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.audit.service.AuditLogService;
import com.nocteon.nocteon_api.common.exception.notFound.OrderNotFoundException;
import com.nocteon.nocteon_api.common.security.ActorResolver;
import com.nocteon.nocteon_api.common.security.AuditActor;
import com.nocteon.nocteon_api.order.dto.request.UpdateOrderStatusRequest;
import com.nocteon.nocteon_api.order.dto.response.OrderResponse;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.entity.OrderStatusHistory;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.order.event.OrderShippedEvent;
import com.nocteon.nocteon_api.order.event.OrderStatusChangedEvent;
import com.nocteon.nocteon_api.order.exception.CancellationReasonRequiredException;
import com.nocteon.nocteon_api.order.exception.InvalidOrderStatusTransitionException;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.order.repository.OrderStatusHistoryRepository;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The order's status machine.
 *
 * <p>Three rules, and the third is the reason this class exists:
 *
 * <ol>
 *   <li>Only the transitions in {@link #ALLOWED_TRANSITIONS} happen. A
 *       delivered or a cancelled order is terminal, a stage is never skipped
 *       and a status never moves backwards.</li>
 *   <li>The history row is written in the SAME TRANSACTION as the status
 *       change, so an order can never carry a status that its history does
 *       not explain.</li>
 *   <li>The cancellation reason is REQUIRED, and the shipment timestamps
 *       follow the status: an order that becomes SHIPPED is stamped as
 *       shipped, and one that becomes DELIVERED is stamped as delivered.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderStatusService {

        private final OrderRepository orderRepository;
        private final OrderStatusHistoryRepository historyRepository;
        private final OrderQueryService orderQueryService;
        private final ProductVariantRepository variantRepository;
        private final ApplicationEventPublisher eventPublisher;
        private final AuditLogService auditLogService;

        /**
         * The transitions the order may make.
         *
         * <p>{@code SHIPPED -> CANCELLED} is NOT here: the state machine does
         * not allow cancelling an order that is already on its way. The
         * contract in ORDERS_CONTRACT.md asks whether that should change.
         */
        private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
                OrderStatus.PENDING, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
                OrderStatus.CONFIRMED, Set.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED),
                OrderStatus.PROCESSING, Set.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
                OrderStatus.SHIPPED, Set.of(OrderStatus.DELIVERED),
                OrderStatus.DELIVERED, Set.of(),
                OrderStatus.CANCELLED, Set.of());

        /** The transitions out of a status — the API and the UI read the same list. */
        public static Set<OrderStatus> allowedTransitions(OrderStatus from) {
                return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        }

        /**
         * Move an order to a new status, without a note and without anyone
         * to notify.
         *
         * <p>For the callers that only move an order: the expiry sweep, the
         * payment webhook.
         */
        @Transactional
        public OrderResponse updateStatus(Long orderId, OrderStatus newStatus) {
                return updateStatus(orderId, newStatus, "system");
        }

        /**
         * Move an order to a new status, without a note and without anyone
         * to notify.
         *
         * <p>For the callers that only move an order: the expiry sweep, the
         * payment webhook.
         */
        @Transactional
        public OrderResponse updateStatus(Long orderId, OrderStatus newStatus, String actor) {
                UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
                request.setStatus(newStatus);
                if (newStatus == OrderStatus.CANCELLED) {
                        // A system cancellation still has to say why: the
                        // order page shows the reason, and a blank one is a
                        // question nobody can answer later.
                        request.setReason("Cancelled by the system.");
                }
                return updateStatus(orderId, request, actor);
        }

        /**
         * Move an order to a new status, for a person at the dashboard.
         *
         * <p>Separate from {@link #updateStatus(Long, UpdateOrderStatusRequest, String)}
         * for ONE reason: a refusal. "Who tried to move a delivered order back" is a
         * question the log has to answer, so a refused change keeps its audit row —
         * and the refusal writes nothing else, so there is nothing for the rollback to
         * protect. The internal callers (the expiry sweep) keep the plain method: their
         * transaction has already written the payment's failure, and a refusal must not
         * commit half of that.
         *
         * <p>Delegates on {@code this}, so the outer transaction (and its
         * {@code noRollbackFor}) is the one that governs.
         */
        @Transactional(noRollbackFor = { InvalidOrderStatusTransitionException.class,
                        CancellationReasonRequiredException.class })
        public OrderResponse updateStatusForAdmin(Long orderId, UpdateOrderStatusRequest request, String actor) {
                return updateStatus(orderId, request, actor);
        }

        /**
         * Move an order to a new status.
         *
         * @param orderId the order
         * @param request the new status, the note, the notify flag and the reason
         * @param actor   who moved it: the e-mail of the admin, or the system
         */
        @Transactional
        public OrderResponse updateStatus(Long orderId, UpdateOrderStatusRequest request, String actor) {
                Order order = orderRepository.findById(orderId)
                                .orElseThrow(OrderNotFoundException::new);

                OrderStatus currentStatus = order.getStatus();
                OrderStatus newStatus = request.getStatus();

                if (!allowedTransitions(currentStatus).contains(newStatus)) {
                        // A refusal is evidence too: "who tried to move a delivered
                        // order back" is a question the log has to answer. The row
                        // records the change that was asked for and the reason it
                        // was refused.
                        auditRefusedStatusChange(order, currentStatus, newStatus,
                                        "The order cannot move from " + currentStatus + " to " + newStatus + ".");
                        throw new InvalidOrderStatusTransitionException(currentStatus, newStatus);
                }

                if (newStatus == OrderStatus.CANCELLED
                                && (request.getReason() == null || request.getReason().isBlank())) {
                        auditRefusedStatusChange(order, currentStatus, newStatus,
                                        "A cancellation needs a reason.");
                        throw new CancellationReasonRequiredException();
                }

                order.setStatus(newStatus);

                if (newStatus == OrderStatus.CANCELLED) {
                        order.setCancellationReason(request.getReason());
                        releaseStock(order);
                } else if (newStatus == OrderStatus.SHIPPED) {
                        if (order.getShippedAt() == null) {
                                order.setShippedAt(Instant.now());
                        }
                } else if (newStatus == OrderStatus.DELIVERED) {
                        if (order.getShippedAt() == null) {
                                order.setShippedAt(Instant.now());
                        }
                        if (order.getDeliveredAt() == null) {
                                order.setDeliveredAt(Instant.now());
                        }
                }

                // The audit row joins this transaction: a status change that is
                // rolled back afterwards leaves no row claiming it happened. Only
                // the fields that actually moved are recorded (the status, the
                // cancellation reason, the shipment stamps).
                //
                // `order` is captured BEFORE save: production returns the same
                // instance, but a mock hands back null and the row must not depend
                // on that.
                Order changed = order;
                order = orderRepository.save(order);
                auditStatusChange(changed, currentStatus, newStatus, request);

                // The history row is written in the same transaction: if this
                // fails, the status change is rolled back with it.
                historyRepository.save(new OrderStatusHistory(
                                order,
                                currentStatus,
                                newStatus,
                                request.getNote() != null && !request.getNote().isBlank()
                                                ? request.getNote()
                                                : request.getReason(),
                                actor,
                                request.isNotifyCustomer()));

                if (newStatus == OrderStatus.SHIPPED) {
                        eventPublisher.publishEvent(new OrderShippedEvent(order.getId()));
                }

                if (request.isNotifyCustomer()) {
                        // The customer is to be told. The e-mail is enqueued
                        // AFTER the transaction commits, so nobody is told
                        // about a change that was rolled back.
                        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                                        order.getId(),
                                        currentStatus,
                                        newStatus,
                                        request.getNote() != null && !request.getNote().isBlank()
                                                        ? request.getNote()
                                                        : request.getReason()));
                }

                log.info("Order {} status changed from {} to {} by {}", orderId, currentStatus, newStatus, actor);

                return orderQueryService.buildResponse(order);
        }

        /**
         * The history of an order, oldest first.
         *
         * <p>The chain reads forwards: a reader follows the order from placed
         * to where it is now.
         */
        @Transactional(readOnly = true)
        public List<OrderStatusHistory> getHistory(Long orderId) {
                if (!orderRepository.existsById(orderId)) {
                        throw new OrderNotFoundException();
                }
                return historyRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
        }

        /**
         * Write the FIRST history row: the order was placed.
         *
         * <p>Called by the checkout, in the transaction that creates the order.
         */
        @Transactional
        public void recordCreation(Order order, OrderStatus initialStatus, String actor) {
                historyRepository.save(new OrderStatusHistory(order, null, initialStatus, null, actor, false));
        }

        /** Give the reserved stock back to the shelf. */
        private void releaseStock(Order order) {
                for (var item : order.getItems()) {
                        variantRepository.releaseStock(item.getProductVariant().getId(), item.getQuantity());
                }
        }

        // ─── the audit rows ──────────────────────────────────────────────────

        /**
         * "Who moved this order, and from what to what."
         *
         * <p>A cancellation is its own action ({@code ORDER_CANCEL}) because that
         * is the sentence an operator searches for; every other move is
         * {@code ORDER_STATUS_CHANGE}. Only the fields the change touched are
         * recorded, and timestamps are written as ISO strings: the audit writer
         * serialises with a plain ObjectMapper, which has no java.time support.
         */
        private void auditStatusChange(Order order, OrderStatus from, OrderStatus to,
                        UpdateOrderStatusRequest request) {
                AuditAction action = to == OrderStatus.CANCELLED
                                ? AuditAction.ORDER_CANCEL
                                : AuditAction.ORDER_STATUS_CHANGE;

                Map<String, Object> oldValues = new LinkedHashMap<>();
                oldValues.put("status", from.name());

                Map<String, Object> newValues = new LinkedHashMap<>();
                newValues.put("status", to.name());
                if (to == OrderStatus.CANCELLED) {
                        newValues.put("cancellationReason", order.getCancellationReason());
                }
                if (order.getShippedAt() != null && from != OrderStatus.SHIPPED
                                && from != OrderStatus.DELIVERED) {
                        newValues.put("shippedAt", order.getShippedAt().toString());
                }
                if (order.getDeliveredAt() != null && from != OrderStatus.DELIVERED) {
                        newValues.put("deliveredAt", order.getDeliveredAt().toString());
                }

                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("note", request.getNote());
                metadata.put("notifyCustomer", request.isNotifyCustomer());

                AuditActor actor = ActorResolver.currentAuditActor();
                auditLogService.logSuccess(action, actor.userId(), actor.userEmail(),
                                "Order", order.getId(), order.getOrderNumber(),
                                null, null, oldValues, newValues, metadata);
        }

        /** The row for a transition that was REFUSED: no change, but the attempt is kept. */
        private void auditRefusedStatusChange(Order order, OrderStatus from, OrderStatus to, String reason) {
                AuditAction action = to == OrderStatus.CANCELLED
                                ? AuditAction.ORDER_CANCEL
                                : AuditAction.ORDER_STATUS_CHANGE;

                Map<String, Object> oldValues = new LinkedHashMap<>();
                oldValues.put("status", from == null ? null : from.name());

                Map<String, Object> newValues = new LinkedHashMap<>();
                newValues.put("status", to == null ? null : to.name());

                AuditActor actor = ActorResolver.currentAuditActor();
                auditLogService.logFailure(action, actor.userId(), actor.userEmail(),
                                "Order", order.getId(), order.getOrderNumber(),
                                null, null, oldValues, newValues, null, reason);
        }
}
