package com.nocteon.nocteon_api.mail.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import  com.nocteon.nocteon_api.mail.dto.BrevoSender;
import com.nocteon.nocteon_api.mail.config.BrevoTemplateProperties;
import com.nocteon.nocteon_api.mail.dto.BrevoEmailRequest;
import com.nocteon.nocteon_api.mail.dto.BrevoEmailResponse;
import com.nocteon.nocteon_api.mail.dto.BrevoRecipient;
import com.nocteon.nocteon_api.mail.enums.EmailType;
import com.nocteon.nocteon_api.mail.exception.BrevoApiException;
import com.nocteon.nocteon_api.mail.exception.BrevoRetryableException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class BrevoClient {

    private final WebClient brevoWebClient; 
    private final BrevoTemplateProperties templateProperties;

    public String sendTransactionalEmail(String recipientEmail, EmailType type, Map<String, Object> params) {

        BrevoEmailRequest request = buildRequest(recipientEmail, type, params);

        try {
            BrevoEmailResponse response = brevoWebClient.post()
                    .uri("/smtp/email")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, this::handleClientError)
                    .onStatus(HttpStatusCode::is5xxServerError, this::handleServerError)
                    .bodyToMono(BrevoEmailResponse.class)
                    .block(Duration.ofSeconds(10)); 

            if (response == null || response.messageId() == null) {
                throw new BrevoApiException("Empty response from Brevo");
            }

            return response.messageId();

        } catch (WebClientResponseException ex) {
            throw new BrevoApiException("Brevo API call failed: " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            throw new BrevoApiException("Unexpected error calling Brevo: " + ex.getMessage(), ex);
        }
    }

    private BrevoEmailRequest buildRequest(String recipientEmail, EmailType type, Map<String, Object> params) {
        return new BrevoEmailRequest(
                new BrevoSender("Nocteon", "orders@nocteon.com"),
                List.of(new BrevoRecipient(recipientEmail)),
                resolveTemplateId(type),
                params);
    }

    private Long resolveTemplateId(EmailType type) {
        return switch (type) {
            case ORDER_CONFIRMATION -> templateProperties.getOrderConfirmation();
            case PAYMENT_SUCCESS -> templateProperties.getPaymentSuccess();
            case ABANDONED_CART -> templateProperties.getAbandonedCart();
            case ADMIN_BROADCAST -> templateProperties.getAdminBroadcast();
            case ORDER_SHIPPED -> templateProperties.getOrderShipped();

        };
    }

    private Mono<Throwable> handleClientError(ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> new BrevoApiException("Brevo rejected request (4xx): " + body));
    }

    private Mono<Throwable> handleServerError(ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> new BrevoRetryableException("Brevo server error (5xx): " + body));
    }
}
