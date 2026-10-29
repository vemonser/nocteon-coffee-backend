package com.nocteon.nocteon_api.payment.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.nocteon.nocteon_api.notifications.event.PaymentSucceededEvent;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.payment.dto.request.PaymobCallbackRequest;
import com.nocteon.nocteon_api.payment.dto.request.PaymobTransactionObj;
import com.nocteon.nocteon_api.payment.entity.Payment;
import com.nocteon.nocteon_api.payment.exception.RefundFailedException;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;
import com.nocteon.nocteon_api.payment.repository.PaymentRepository;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;
import com.nocteon.nocteon_api.product.service.InventoryMovementService;

import org.springframework.transaction.annotation.Transactional;
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
    private final ProductVariantRepository variantRepository;
    private final InventoryMovementService inventoryService;
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
        boolean orderTerminal = order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.DELIVERED;

        if (txn.isSuccess()) {
            payment.setStatus(PaymentStatus.PAID);
            payment.setProviderPaymentId(txn.getId().toString());
            payment.setPaymentMethod(
                    txn.getSourceData() != null ? txn.getSourceData().getSubType() : null);
            payment.setPaidAt(Instant.now());
            if (orderTerminal) {
                // Money was captured but the order is already dead (cancelled by an
                // admin, or already delivered). Record the money truth, keep the
                // order untouched, and let ops refund manually — resurrecting it
                // would ship something already written off, on stock that the
                // cancellation already gave back to the shelf.
                order.setPaymentStatus(PaymentStatus.PAID);
                log.warn("Late success webhook for terminal order {} (status {}). "
                        + "Payment recorded as PAID, order left untouched — manual refund may be needed.",
                        order.getId(), order.getStatus());
            } else {
                order.setStatus(OrderStatus.CONFIRMED);
                order.setPaymentStatus(PaymentStatus.PAID);
                // The units are consumed through InventoryMovementService, so
                // the payment writes its stock movement (FE-13) in the same
                // transaction as the payment row: a webhook that fails half way
                // leaves neither.
                order.getItems().forEach(item -> {
                    ProductVariant variant = item.getProductVariant();
                    InventoryMovementService.StockChange change = inventoryService.consumeForOrder(
                            variant.getId(), item.getQuantity(), "system:payment-webhook", order.getId());
                    if (change != null && change.crossedReorderThreshold(variant.getReorderThreshold())) {
                        eventPublisher.publishEvent(new com.nocteon.nocteon_api.notifications.event.StockChangedEvent(
                                variant.getId(), variant.getProduct().getSlug(), variant.getSku(),
                                change.before(), change.after(), variant.getReorderThreshold()));
                    }
                });
                eventPublisher.publishEvent(new PaymentSucceededEvent(order.getId(), payment.getAmount()));
            }

        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(
                    txn.getData() != null ? txn.getData().getMessage() : "Payment failed");
            if (!orderTerminal) {
                order.setStatus(OrderStatus.CANCELLED);
                order.setPaymentStatus(PaymentStatus.FAILED);

                order.getItems().forEach(item -> {
                    ProductVariant variant = item.getProductVariant();
                    variantRepository.releaseStock(variant.getId(), item.getQuantity());
                });
            } else {
                // Already dead: record the payment failure, but the stock that a
                // previous cancellation restored must not be restored twice.
                log.warn("Failure webhook for already-terminal order {} (status {}). "
                        + "Payment marked FAILED, stock left untouched.",
                        order.getId(), order.getStatus());
            }
        }

        if (payment.getStatus() == com.nocteon.nocteon_api.payment.enums.PaymentStatus.FAILED) {
            eventPublisher.publishEvent(new com.nocteon.nocteon_api.notifications.event.PaymentFailedEvent(order.getId(), payment.getAmount()));
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

            return MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8),
                    receivedHmac.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("HMAC verification failed", e);
            return false;
        }
    }

    /**
     * Refund a payment through the gateway.
     *
     * <p>The money leaves the store HERE or nowhere: the refund is recorded
     * only when the gateway has accepted it, so a refund row always means
     * the customer has the money back.
     *
     * <p>Paymob's Accept API takes the PARENT transaction id and the amount in
     * CENTS, and answers with the refund's own transaction id — which is
     * stored as the refund's gateway reference.
     *
     * @param payment the payment to refund
     * @param amount  how much to give back
     * @return the gateway's own reference for the refund
     */
    public String refund(Payment payment, BigDecimal amount) {
        if (payment.getProviderPaymentId() == null || payment.getProviderPaymentId().isBlank()) {
                throw new RefundFailedException(
                                "The payment has no gateway transaction, so it cannot be refunded through the gateway.");
        }

        int amountCents = amount.multiply(BigDecimal.valueOf(100)).intValueExact();

        Map<String, Object> body = new HashMap<>();
        body.put("parent_transaction_id", payment.getProviderPaymentId());
        body.put("amount_cents", String.valueOf(amountCents));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(getAuthToken());

        Map<String, Object> response;
        try {
                response = restTemplate.exchange(
                                "https://accept.paymob.com/api/acceptance/refunds",
                                HttpMethod.POST,
                                new HttpEntity<>(body, headers),
                                new ParameterizedTypeReference<Map<String, Object>>() {
                                }).getBody();
        } catch (RestClientException ex) {
                log.warn("Paymob refused the refund of payment {}: {}", payment.getId(), ex.getMessage());
                throw new RefundFailedException(
                                "The payment gateway refused the refund: " + ex.getMessage(), ex);
        }

        if (response == null || response.get("id") == null) {
                throw new RefundFailedException("The payment gateway did not accept the refund.");
        }

        String reference = response.get("id").toString();
        log.info("Paymob refund {} for payment {} ({} cents)", reference, payment.getId(), amountCents);
        return reference;
    }

    @SuppressWarnings("unchecked")
    private String getAuthToken() {
        Map<String, Object> body = new HashMap<>();
        body.put("api_key", apiKey);

        Map<String, Object> response = (Map<String, Object>) restTemplate.postForObject(
                "https://accept.paymob.com/api/auth/tokens",
                body, Map.class);

        if (response == null || response.get("token") == null) {
            throw new IllegalStateException("Failed to obtain Paymob auth token");
        }
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

        if (response == null || response.get("id") == null) {
            throw new IllegalStateException("Failed to create Paymob order");
        }
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

        if (response == null || response.get("token") == null) {
            throw new IllegalStateException("Failed to obtain Paymob payment key");
        }
        return (String) response.get("token");
    }
}