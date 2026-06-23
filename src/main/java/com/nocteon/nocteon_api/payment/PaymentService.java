package com.nocteon.nocteon_api.payment;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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

        private final RestTemplate restTemplate;

        public String createPayment(BigDecimal amount, Long orderId,
                        String firstName, String lastName, String email, String phone) {

                String authToken = getAuthToken();
                String paymobOrderId = createPaymobOrder(authToken, amount, orderId);
                String paymentKey = getPaymentKey(
                                authToken, paymobOrderId, amount,
                                firstName, lastName, email, phone);

                return "https://accept.paymob.com/api/acceptance/iframes/"
                                + iframeId + "?payment_token=" + paymentKey;
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
        private String createPaymobOrder(String authToken,
                        BigDecimal amount, Long orderId) {

                Map<String, Object> body = new HashMap<>();
                body.put("auth_token", authToken);
                body.put("delivery_needed", false);
                body.put("amount_cents",
                                amount.multiply(BigDecimal.valueOf(100)).intValue());
                body.put("currency", "EGP");
                body.put("merchant_order_id", orderId.toString());
                body.put("items", List.of());

                Map<String, Object> response = (Map<String, Object>) restTemplate.postForObject(
                                "https://accept.paymob.com/api/ecommerce/orders",
                                body, Map.class);

                return response.get("id").toString();
        }

        @SuppressWarnings("unchecked")
        private String getPaymentKey(String authToken, String paymobOrderId,
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
                body.put("order_id", paymobOrderId);
                body.put("billing_data", billingData);
                body.put("currency", "EGP");
                body.put("integration_id", Integer.parseInt(integrationId));

                Map<String, Object> response = (Map<String, Object>) restTemplate.postForObject(
                                "https://accept.paymob.com/api/acceptance/payment_keys",
                                body, Map.class);

                return (String) response.get("token");
        }

        // TODO: Implement HMAC verification for Paymob webhook
        public boolean verifyWebhook(Map<String, String> params, String hmacSecret) {
                return true;
        }
}