package com.nocteon.nocteon_api.mail.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;

/**
 * The provider-side template ids, read from {@code application.brevo.templates.*}.
 *
 * <p>The YAML puts them under a {@code templates} map (they are all template
 * ids and nothing else), so the properties bind as a map and the accessors read
 * it. Flat fields under the {@code application.brevo} prefix silently bound to
 * NULL — the keys live one level deeper — and every confirmation went out with
 * no template and no body, which Brevo rejects with 4xx
 * "Either of htmlContent or textContent is required".
 *
 * <p>The accessor names are the ones the client already calls, so nobody else
 * had to change.
 */
@Component
@ConfigurationProperties(prefix = "application.brevo")
@Getter
public class BrevoTemplateProperties {

    /** template key (as written in the YAML) → Brevo template id. */
    private Map<String, Long> templates = new HashMap<>();

    public Long getOrderConfirmation() {
        return templates.get("order-confirmation");
    }

    public Long getPaymentSuccess() {
        return templates.get("payment-success");
    }

    public Long getAbandonedCart() {
        return templates.get("abandoned-cart");
    }

    public Long getAdminBroadcast() {
        return templates.get("admin-broadcast");
    }

    public Long getOrderShipped() {
        return templates.get("order-shipped");
    }

    public Long getOrderStatusChanged() {
        return templates.get("order-status-changed");
    }

    public Long getVerification() {
        return templates.get("verification");
    }

    public Long getPasswordReset() {
        return templates.get("password-reset");
    }
}
