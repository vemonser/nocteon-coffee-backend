package com.nocteon.nocteon_api.payment.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.nocteon.nocteon_api.notifications.event.PaymentSucceededEvent;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.payment.dto.request.PaymobCallbackRequest;
import com.nocteon.nocteon_api.payment.dto.request.PaymobTransactionObj;
import com.nocteon.nocteon_api.payment.entity.Payment;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;
import com.nocteon.nocteon_api.payment.repository.PaymentRepository;
import com.nocteon.nocteon_api.product.entity.ProductVariant;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    @Value("${application.paymob.api-key}")
    private String apiKey;

    @Value("${application.paymob.integration-id}")
    private String integrationId;

    @Value("${application.paymob.iframe-id}")
    private String iframeId;

    @Value("${application.paymob.hmac-secret}")
    private String hmacSecret;

    private final RestTemplate restTemplate;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public String initiatePayment(Order order, String firstName,
            String lastName, String email, String phone) {
        int attemptNumber = paymentRepository.countByOrderId(order.getId()) + 1;

        Payment payment = Payment.builder()
                .order(order)
                .attemptNumber(attemptNumber)
                .amount(order.getTotalAmount())
                .currency("EGP")
                .status(PaymentStatus.PENDING)
                .build();
        payment = paymentRepository.save(payment);

        // 2. Auth Token
        String authToken = getAuthToken();

        // 3. Create Order في Paymob - باستخدام payment.getId()
        String providerOrderId = createPaymobOrder(authToken, order, payment.getId(), attemptNumber);

        // 4. Payment Key
        String paymentKey = getPaymentKey(
                authToken, providerOrderId, order.getTotalAmount(),
                firstName, lastName, email, phone);

        // 5. حدّث الـ Payment بالـ providerOrderId
        payment.setProviderOrderId(providerOrderId);
        paymentRepository.save(payment);

        return "https://accept.paymob.com/api/acceptance/iframes/"
                + iframeId + "?payment_token=" + paymentKey;
    }

    @Transactional
    public void handleCallback(String hmac, PaymobCallbackRequest payload) {

        PaymobTransactionObj txn = payload.getObj();

        if (txn == null) {
            log.warn("Webhook payload missing 'obj'");
            return;
        }

        if (!verifyHmac(txn, hmac)) {
            log.warn("Invalid HMAC for payment callback, transaction id: {}", txn.getId());
            return;
        }

        String providerOrderId = txn.getOrder() != null
                ? txn.getOrder().getId().toString()
                : null;

        if (providerOrderId == null) {
            log.warn("Webhook missing order id");
            return;
        }

        Payment payment = paymentRepository
                .findByProviderOrderIdForUpdate(providerOrderId)
                .orElse(null);

        if (payment == null) {
            log.warn("Payment not found for provider order: {}", providerOrderId);
            return;
        }

        if (payment.getStatus() == PaymentStatus.PAID || payment.getStatus() == PaymentStatus.FAILED) {
            log.info("Payment {} already processed with status {}, ignoring duplicate webhook",
                    payment.getId(), payment.getStatus());
            return;
        }

        Order order = payment.getOrder();

        if (txn.isSuccess()) {
            payment.setStatus(PaymentStatus.PAID);
            payment.setProviderPaymentId(txn.getId().toString());
            payment.setPaymentMethod(
                    txn.getSourceData() != null ? txn.getSourceData().getSubType() : null);
            payment.setPaidAt(Instant.now());
            order.setStatus(OrderStatus.CONFIRMED);
            order.setPaymentStatus(PaymentStatus.PAID);
            eventPublisher.publishEvent(new PaymentSucceededEvent(order.getId(), payment.getAmount()));

        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(
                    txn.getData() != null ? txn.getData().getMessage() : "Payment failed");
            order.setStatus(OrderStatus.CANCELLED);
            order.setPaymentStatus(PaymentStatus.FAILED);

            order.getItems().forEach(item -> {
                ProductVariant variant = item.getProductVariant();
                variant.setStockQuantity(variant.getStockQuantity() + item.getQuantity());
            });
        }

        paymentRepository.save(payment);
        orderRepository.save(order);

        log.info("Payment {} for order {} - status: {}",
                txn.getId(), order.getId(), payment.getStatus());
    }

    private boolean verifyHmac(PaymobTransactionObj txn, String receivedHmac) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(txn.getAmountCents());
            sb.append(txn.getCreatedAt());
            sb.append(txn.getCurrency());
            sb.append(txn.isErrorOccured());
            sb.append(txn.isHasParentTransaction());
            sb.append(txn.getId());
            sb.append(txn.getIntegrationId());
            sb.append(txn.is3dSecure());
            sb.append(txn.isAuth());
            sb.append(txn.isCapture());
            sb.append(txn.isRefunded());
            sb.append(txn.isStandalonePayment());
            sb.append(txn.isVoided());
            sb.append(txn.getOrder() != null ? txn.getOrder().getId() : "");
            sb.append(txn.getOwner());
            sb.append(txn.isPending());
            sb.append(txn.getSourceData() != null ? txn.getSourceData().getPan() : "");
            sb.append(txn.getSourceData() != null ? txn.getSourceData().getSubType() : "");
            sb.append(txn.getSourceData() != null ? txn.getSourceData().getType() : "");
            sb.append(txn.isSuccess());

            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] hash = mac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));

            String computed = HexFormat.of().formatHex(hash);

            return computed.equals(receivedHmac);
        } catch (Exception e) {
            log.error("HMAC verification failed", e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private String getAuthToken() {
        Map<String, Object> body = new HashMap<>();
        body.put("api_key", apiKey);

        Map<String, Object> response = (Map<String, Object>) restTemplate.postForObject(
                "https://accept.paymob.com/api/auth/tokens",
                body, Map.class);

        return (String) response.get("token");
    }

    @SuppressWarnings("unchecked")
    private String createPaymobOrder(String authToken, Order order, Long paymentId, int attemptNumber) {
        Map<String, Object> body = new HashMap<>();
        body.put("auth_token", authToken);
        body.put("delivery_needed", false);
        body.put("amount_cents",
                order.getTotalAmount()
                        .multiply(BigDecimal.valueOf(100)).intValue());
        body.put("currency", "EGP");
        body.put("merchant_order_id", "order-" + order.getId() + "-attempt-" + attemptNumber);
        body.put("items", List.of());

        Map<String, Object> response = (Map<String, Object>) restTemplate.postForObject(
                "https://accept.paymob.com/api/ecommerce/orders",
                body, Map.class);

        return response.get("id").toString();
    }

    @SuppressWarnings("unchecked")
    private String getPaymentKey(String authToken, String providerOrderId,
            BigDecimal amount, String firstName, String lastName,
            String email, String phone) {

        Map<String, Object> billingData = new HashMap<>();
        billingData.put("first_name", firstName);
        billingData.put("last_name", lastName);
        billingData.put("email", email);
        billingData.put("phone_number", phone);
        billingData.put("apartment", "NA");
        billingData.put("floor", "NA");
        billingData.put("street", "NA");
        billingData.put("building", "NA");
        billingData.put("shipping_method", "NA");
        billingData.put("postal_code", "NA");
        billingData.put("city", "NA");
        billingData.put("country", "EG");
        billingData.put("state", "NA");

        Map<String, Object> body = new HashMap<>();
        body.put("auth_token", authToken);
        body.put("amount_cents",
                amount.multiply(BigDecimal.valueOf(100)).intValue());
        body.put("expiration", 3600);
        body.put("order_id", providerOrderId);
        body.put("billing_data", billingData);
        body.put("currency", "EGP");
        body.put("integration_id", Integer.parseInt(integrationId));

        Map<String, Object> response = (Map<String, Object>) restTemplate.postForObject(
                "https://accept.paymob.com/api/acceptance/payment_keys",
                body, Map.class);

        return (String) response.get("token");
    }
}