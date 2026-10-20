package com.nocteon.nocteon_api.order.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.common.exception.notFound.OrderNotFoundException;
import com.nocteon.nocteon_api.order.dto.request.CreateRefundRequest;
import com.nocteon.nocteon_api.order.dto.response.OrderRefundResponse;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.entity.OrderRefund;
import com.nocteon.nocteon_api.order.exception.InvalidRefundException;
import com.nocteon.nocteon_api.order.repository.OrderRefundRepository;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.payment.entity.Payment;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;
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

        /**
         * Record a refund.
         *
         * @param orderId the order
         * @param request the amount and the reason
         * @param actor   who recorded it
         */
        @Transactional
        public OrderRefundResponse refund(Long orderId, CreateRefundRequest request, String actor) {
                Order order = orderRepository.findById(orderId)
                                .orElseThrow(OrderNotFoundException::new);

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

                order.setPaymentStatus(PaymentStatus.REFUNDED);
                orderRepository.save(order);

                return toResponse(refund);
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
