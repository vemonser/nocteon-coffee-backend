package com.nocteon.nocteon_api.order.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.common.exception.notFound.OrderNotFoundException;
import com.nocteon.nocteon_api.order.dto.request.UpdateOrderStatusRequest;
import com.nocteon.nocteon_api.order.dto.response.OrderResponse;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.entity.OrderStatusHistory;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.order.event.OrderShippedEvent;
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
                        throw new InvalidOrderStatusTransitionException(currentStatus, newStatus);
                }

                if (newStatus == OrderStatus.CANCELLED
                                && (request.getReason() == null || request.getReason().isBlank())) {
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

                order = orderRepository.save(order);

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
}
