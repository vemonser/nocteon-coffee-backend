package com.nocteon.nocteon_api.payment.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
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

    @Transactional
    public String initiatePayment(Order order, String firstName,
            String lastName, String email, String phone) {

        // 1. Auth Token
        String authToken = getAuthToken();

        // 2. Create Order في Paymob
        String providerOrderId = createPaymobOrder(authToken, order);

        // 3. Payment Key
        String paymentKey = getPaymentKey(
                authToken, providerOrderId, order.getTotalAmount(),
                firstName, lastName, email, phone);

        // 4. Save Payment record
        Payment payment = Payment.builder()
                .order(order)
                .providerOrderId(providerOrderId)
                .amount(order.getTotalAmount())
                .currency("EGP")
                .status(PaymentStatus.PENDING)
                .build();

        paymentRepository.save(payment);

        return "https://accept.paymob.com/api/acceptance/iframes/"
                + iframeId + "?payment_token=" + paymentKey;
    }

    @Transactional
    public void handleCallback(Map<String, String> params) {
        String success = params.get("success");
        String providerPaymentId = params.get("id");
        String providerOrderId = params.get("order");
        String paymentMethod = params.get("source_data_sub_type");

        // Verify HMAC
        if (!verifyHmac(params)) {
            log.warn("Invalid HMAC for payment callback");
            return;
        }

        Payment payment = paymentRepository
                .findByProviderOrderId(providerOrderId)
                .orElse(null);

        if (payment == null) {
            log.warn("Payment not found for order: {}", providerOrderId);
            return;
        }

        Order order = payment.getOrder();

        if ("true".equals(success)) {
            payment.setStatus(PaymentStatus.PAID);
            payment.setProviderPaymentId(providerPaymentId);
            payment.setPaymentMethod(paymentMethod);
            payment.setPaidAt(Instant.now());
            order.setStatus(OrderStatus.PAID);
            order.setPaymentStatus("PAID");
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(params.get("data_message"));
            order.setStatus(OrderStatus.CANCELLED);
            order.setPaymentStatus("FAILED");

            // رجّع الـ stock
            order.getItems().forEach(item -> {
                ProductVariant variant = item.getProductVariant();
                variant.setStock(variant.getStock() + item.getQuantity());
            });
        }

        paymentRepository.save(payment);
        orderRepository.save(order);

        log.info("Payment {} for order {} - status: {}",
                providerPaymentId, order.getId(), payment.getStatus());
    }

    private boolean verifyHmac(Map<String, String> params) {
        try {
            String[] keys = {
                "amount_cents", "created_at", "currency",
                "error_occured", "has_parent_transaction", "id",
                "integration_id", "is_3d_secure", "is_auth",
                "is_capture", "is_refunded", "is_standalone_payment",
                "is_voided", "order", "owner", "pending",
                "source_data_pan", "source_data_sub_type",
                "source_data_type", "success"
            };

            StringBuilder sb = new StringBuilder();
            for (String key : keys) {
                sb.append(params.getOrDefault(key, ""));
            }

            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(), "HmacSHA512"));
            byte[] hash = mac.doFinal(sb.toString().getBytes());

            String computed = HexFormat.of().formatHex(hash);
            return computed.equals(params.get("hmac"));
        } catch (Exception e) {
            log.error("HMAC verification failed", e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private String getAuthToken() {
        Map<String, Object> body = new HashMap<>();
        body.put("api_key", apiKey);

        Map<String, Object> response = (Map<String, Object>)
                restTemplate.postForObject(
                        "https://accept.paymob.com/api/auth/tokens",
                        body, Map.class);

        return (String) response.get("token");
    }

    @SuppressWarnings("unchecked")
    private String createPaymobOrder(String authToken, Order order) {
        Map<String, Object> body = new HashMap<>();
        body.put("auth_token", authToken);
        body.put("delivery_needed", false);
        body.put("amount_cents",
                order.getTotalAmount()
                        .multiply(BigDecimal.valueOf(100)).intValue());
        body.put("currency", "EGP");
        body.put("merchant_order_id", order.getId().toString());
        body.put("items", List.of());

        Map<String, Object> response = (Map<String, Object>)
                restTemplate.postForObject(
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

        Map<String, Object> response = (Map<String, Object>)
                restTemplate.postForObject(
                        "https://accept.paymob.com/api/acceptance/payment_keys",
                        body, Map.class);

        return (String) response.get("token");
    }
}