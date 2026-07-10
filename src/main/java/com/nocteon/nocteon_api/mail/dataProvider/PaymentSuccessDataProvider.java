package com.nocteon.nocteon_api.mail.dataProvider;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.mail.enums.EmailType;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.payment.entity.Payment;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;
import com.nocteon.nocteon_api.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentSuccessDataProvider implements EmailTemplateDataProvider {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    @Override
    public EmailType supports() {
        return EmailType.PAYMENT_SUCCESS;
    }

    @Override
    public Map<String, Object> buildParams(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));

        Payment payment = paymentRepository
                .findFirstByOrderIdAndStatusOrderByPaidAtDesc(orderId, PaymentStatus.PAID) 
                .orElseThrow(() -> new IllegalStateException("No successful payment found for order: " + orderId));

        return Map.of(
                "customerName", order.getUser().getProfile().getFullName(),
                "recipientEmail", order.getUser().getEmail(),
                "orderId", order.getId(),
                "transactionId", payment.getProviderPaymentId(),
                "amount", payment.getAmount().toString(),
                "currency", payment.getCurrency(),
                "paidAt", payment.getPaidAt().toString());
    }
}