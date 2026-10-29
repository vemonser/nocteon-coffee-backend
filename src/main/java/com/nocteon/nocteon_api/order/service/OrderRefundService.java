package com.nocteon.nocteon_api.order.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.audit.service.AuditLogService;
import com.nocteon.nocteon_api.common.exception.notFound.OrderNotFoundException;
import com.nocteon.nocteon_api.common.security.ActorResolver;
import com.nocteon.nocteon_api.common.security.AuditActor;
import com.nocteon.nocteon_api.order.dto.request.CreateRefundRequest;
import com.nocteon.nocteon_api.order.dto.response.OrderRefundResponse;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.entity.OrderRefund;
import com.nocteon.nocteon_api.order.exception.InvalidRefundException;
import com.nocteon.nocteon_api.order.repository.OrderRefundRepository;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.payment.entity.Payment;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;
import com.nocteon.nocteon_api.payment.exception.RefundFailedException;
import com.nocteon.nocteon_api.payment.repository.PaymentRepository;
import com.nocteon.nocteon_api.payment.service.PaymentService;

import lombok.RequiredArgsConstructor;

/**
 * The refunds of an order.
 *
 * <p>WHAT THIS DOES NOT DO: it does not return the money. The payment
 * gateway has no refund call wired into this service, so the refund is
 * RECORDED — the amount, the reason, who did it — and the order's payment
 * status becomes REFUNDED. The row says the refund is MANUAL until a gateway
 * reference is written, so nobody believes the customer already has the
 * money back.
 *
 * <p>The amount matters: without it the net paid of an order cannot be
 * computed, and a `REFUNDED` flag says only THAT a refund happened.
 */
@Service
@RequiredArgsConstructor
public class OrderRefundService {

        private final OrderRepository orderRepository;
        private final OrderRefundRepository refundRepository;
        private final PaymentRepository paymentRepository;
        private final PaymentService paymentService;
        private final AuditLogService auditLogService;

        /**
         * Record a refund.
         *
         * <p>The audit row is written only AFTER the gateway has accepted the
         * refund, in this same transaction: the log says "the money went back",
         * so a refund the gateway refused — or one this transaction rolls back —
         * leaves no success row. A refusal is recorded by
         * {@link #auditRefusedRefund} as a {@code success = false} row carrying the
         * change that was asked for and the reason it did not happen; the refusal
         * does not roll the transaction back, so that row survives.
         *
         * @param orderId the order
         * @param request the amount and the reason
         * @param actor   who recorded it
         */
        @Transactional(noRollbackFor = { InvalidRefundException.class, RefundFailedException.class })
        public OrderRefundResponse refund(Long orderId, CreateRefundRequest request, String actor) {
                Order order = orderRepository.findById(orderId)
                                .orElseThrow(OrderNotFoundException::new);

                try {
                        return refundOrFail(order, request, actor);
                } catch (InvalidRefundException | RefundFailedException e) {
                        auditRefusedRefund(order, request, refusalSentence(e));
                        throw e;
                }
        }

        /**
         * The refusal in the operator's words.
         *
         * <p>{@code getMessage()} on these exceptions is the message KEY
         * ({@code error.order.refundNotPaid}), and the audit page prints what it is
         * given — a key on that page is the developer text rule 9 forbids. So the
         * sentence is written here, with the gateway's own detail appended when the
         * refusal came from outside.
         */
        private static String refusalSentence(RuntimeException e) {
                if (e instanceof RefundFailedException failed) {
                        String detail = failed.getMessageArg();
                        return detail == null || detail.isBlank()
                                        ? "The payment gateway refused the refund."
                                        : "The payment gateway refused the refund: " + detail;
                }
                String key = ((com.nocteon.nocteon_api.common.exception.BaseApiException) e).getMessageKey();
                return switch (key) {
                        case "error.order.refundNotPaid" ->
                                "The order was never paid, so there is nothing to refund.";
                        case "error.order.refundTooMuch" ->
                                "The amount is more than what is left to refund.";
                        default -> "The refund was refused.";
                };
        }

        /** The refund itself; every refusal it raises becomes an audit row in {@link #refund}. */
        private OrderRefundResponse refundOrFail(Order order, CreateRefundRequest request, String actor) {
                Long orderId = order.getId();

                if (order.getPaymentStatus() != PaymentStatus.PAID
                                && order.getPaymentStatus() != PaymentStatus.REFUNDED) {
                        throw new InvalidRefundException("error.order.refundNotPaid");
                }

                BigDecimal refundedAlready = totalRefunded(orderId);
                BigDecimal paid = paidAmount(order);
                BigDecimal remaining = paid.subtract(refundedAlready);

                if (request.getAmount().compareTo(remaining) > 0) {
                        throw new InvalidRefundException("error.order.refundTooMuch");
                }

                Payment payment = paymentRepository
                                .findFirstByOrderIdOrderByAttemptNumberDesc(orderId)
                                .orElse(null);

                if (payment == null) {
                        // There is no payment to refund: the order was never
                        // paid through the gateway, so there is nothing to
                        // give back and nothing to record.
                        throw new InvalidRefundException("error.order.refundNotPaid");
                }

                // The MONEY LEAVES THE STORE HERE. The refund is recorded
                // only when the gateway has accepted it — a refund row
                // without a gateway reference would be a promise the store
                // cannot keep.
                String gatewayReference = paymentService.refund(payment, request.getAmount());

                OrderRefund refund = refundRepository.save(new OrderRefund(
                                order,
                                payment,
                                request.getAmount(),
                                request.getReason(),
                                actor,
                                gatewayReference));

                PaymentStatus paymentStatusBefore = order.getPaymentStatus();
                order.setPaymentStatus(PaymentStatus.REFUNDED);
                orderRepository.save(order);

                auditRefund(order, refund, paymentStatusBefore, gatewayReference);

                return toResponse(refund);
        }

        /**
         * "Who gave this money back, how much, and why."
         *
         * <p>The amount and the gateway reference are the two facts a reader needs.
         * Nothing secret is recorded: no card number, no token — the payment row
         * never held more than the gateway's own reference.
         */
        private void auditRefund(Order order, OrderRefund refund, PaymentStatus paymentStatusBefore,
                        String gatewayReference) {
                Map<String, Object> oldValues = new LinkedHashMap<>();
                oldValues.put("paymentStatus",
                                paymentStatusBefore == null ? null : paymentStatusBefore.name());

                Map<String, Object> newValues = new LinkedHashMap<>();
                newValues.put("paymentStatus", PaymentStatus.REFUNDED.name());
                newValues.put("amount", refund.getAmount());
                newValues.put("gatewayReference", gatewayReference);

                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("refundId", refund.getId());
                metadata.put("reason", refund.getReason());
                metadata.put("manual", refund.isManual());

                AuditActor actor = ActorResolver.currentAuditActor();
                auditLogService.logSuccess(AuditAction.ORDER_REFUND, actor.userId(), actor.userEmail(),
                                "Order", order.getId(), order.getOrderNumber(),
                                null, null, oldValues, newValues, metadata);
        }

        /** The row for a refund that did NOT happen: the gateway refused, or the amount was impossible. */
        private void auditRefusedRefund(Order order, CreateRefundRequest request, String reason) {
                Map<String, Object> oldValues = new LinkedHashMap<>();
                oldValues.put("paymentStatus",
                                order.getPaymentStatus() == null ? null : order.getPaymentStatus().name());

                Map<String, Object> newValues = new LinkedHashMap<>();
                newValues.put("paymentStatus", PaymentStatus.REFUNDED.name());
                newValues.put("amount", request.getAmount());

                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("refundReason", request.getReason());

                AuditActor actor = ActorResolver.currentAuditActor();
                auditLogService.logFailure(AuditAction.ORDER_REFUND, actor.userId(), actor.userEmail(),
                                "Order", order.getId(), order.getOrderNumber(),
                                null, null, oldValues, newValues, metadata, reason);
        }

        /** The refunds of an order, oldest first. */
        @Transactional(readOnly = true)
        public List<OrderRefundResponse> getRefunds(Long orderId) {
                if (!orderRepository.existsById(orderId)) {
                        throw new OrderNotFoundException();
                }
                return refundRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                                .map(OrderRefundService::toResponse)
                                .toList();
        }

        /** How much has been given back so far. */
        @Transactional(readOnly = true)
        public BigDecimal totalRefunded(Long orderId) {
                return refundRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                                .map(OrderRefund::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /**
         * The money that came in.
         *
         * <p>The PAYMENT row knows the amount; the order's total is what the
         * store recorded. The payment wins when there is one — it is the
         * amount the customer actually paid.
         */
        private BigDecimal paidAmount(Order order) {
                return paymentRepository
                                .findFirstByOrderIdOrderByAttemptNumberDesc(order.getId())
                                .map(Payment::getAmount)
                                .orElse(order.getTotalAmount());
        }

        static OrderRefundResponse toResponse(OrderRefund refund) {
                return OrderRefundResponse.builder()
                                .id(refund.getId())
                                .orderId(refund.getOrder() != null ? refund.getOrder().getId() : null)
                                .amount(refund.getAmount())
                                .reason(refund.getReason())
                                .createdBy(refund.getCreatedBy())
                                .manual(refund.isManual())
                                .gatewayReference(refund.getGatewayReference())
                                .createdAt(refund.getCreatedAt())
                                .build();
        }
}
