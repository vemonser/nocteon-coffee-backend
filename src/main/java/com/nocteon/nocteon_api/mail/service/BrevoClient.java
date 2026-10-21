package com.nocteon.nocteon_api.mail.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import com.nocteon.nocteon_api.mail.dto.BrevoSender;
import com.nocteon.nocteon_api.mail.config.BrevoTemplateProperties;
import com.nocteon.nocteon_api.mail.dto.BrevoEmailRequest;
import com.nocteon.nocteon_api.mail.dto.BrevoEmailResponse;
import com.nocteon.nocteon_api.mail.dto.BrevoRawEmailRequest;
import com.nocteon.nocteon_api.mail.dto.BrevoRecipient;
import com.nocteon.nocteon_api.mail.enums.EmailType;
import com.nocteon.nocteon_api.mail.exception.BrevoApiException;
import com.nocteon.nocteon_api.mail.exception.BrevoRetryableException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class BrevoClient {

    private final RestClient brevoRestClient;
    private final BrevoTemplateProperties templateProperties;

    public String sendTransactionalEmail(String recipientEmail, EmailType type, Map<String, Object> params) {

        BrevoEmailRequest request = buildRequest(recipientEmail, type, params);

        try {
            BrevoEmailResponse response = brevoRestClient.post()
                    .uri("/smtp/email")
                    .body(request)
                    .retrieve()
                    .body(BrevoEmailResponse.class);

            if (response == null || response.messageId() == null) {
                throw new BrevoApiException("Empty response from Brevo");
            }

            return response.messageId();

        } catch (HttpClientErrorException ex) {
            throw new BrevoApiException("Brevo rejected request (4xx): " + ex.getResponseBodyAsString(), ex);
        } catch (HttpServerErrorException ex) {
            throw new BrevoRetryableException("Brevo server error (5xx): " + ex.getResponseBodyAsString());
        } catch (BrevoApiException ex) {
            throw ex;
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
            case VERIFICATION -> templateProperties.getVerification();
            case PASSWORD_RESET -> templateProperties.getPasswordReset();
            // A contact reply carries its own body, rendered in this
            // repository, so it never goes through a provider-side template.
            case CONTACT_REPLY -> throw new IllegalStateException(
                    "A contact reply is sent as raw content, not through a template");
        };
    }

    /**
     * Sends a message that carries its own subject and HTML body.
     *
     * <p>The other form of the Brevo call — a template id plus params — is
     * used by the transactional templates. This one is for the bodies that
     * live in this repository.
     */
    public String sendRawTransactionalEmail(String recipientEmail, String subject, String htmlContent) {
        BrevoRawEmailRequest request = buildRawRequest(recipientEmail, subject, htmlContent);

        try {
            BrevoEmailResponse response = brevoRestClient.post()
                    .uri("/smtp/email")
                    .body(request)
                    .retrieve()
                    .body(BrevoEmailResponse.class);

            if (response == null || response.messageId() == null) {
                throw new BrevoApiException("Empty response from Brevo");
            }

            return response.messageId();

        } catch (HttpClientErrorException ex) {
            throw new BrevoApiException("Brevo rejected request (4xx): " + ex.getResponseBodyAsString(), ex);
        } catch (HttpServerErrorException ex) {
            throw new BrevoRetryableException("Brevo server error (5xx): " + ex.getResponseBodyAsString());
        } catch (BrevoApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BrevoApiException("Unexpected error calling Brevo: " + ex.getMessage(), ex);
        }
    }

    /**
     * The request for a raw-content send: a subject and an HTML body, and NO
     * template id.
     *
     * <p>Brevo accepts either a templateId + params, or a subject + htmlContent
     * — not both. The contact reply uses the second form, so its body can
     * live in this repository instead of in the Brevo dashboard.
     */
    BrevoRawEmailRequest buildRawRequest(String recipientEmail, String subject, String htmlContent) {
        return new BrevoRawEmailRequest(
                new BrevoSender("Nocteon", "orders@nocteon.com"),
                List.of(new BrevoRecipient(recipientEmail)),
                subject,
                htmlContent);
    }
}