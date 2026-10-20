package com.nocteon.nocteon_api.mail.dto;

import java.util.List;

/**
 * A Brevo send that carries its own content.
 *
 * <p>Brevo accepts EITHER a templateId + params, OR a subject + htmlContent —
 * not both. This is the second form, used by the contact reply so its body
 * can live in this repository.
 */
public record BrevoRawEmailRequest(
        BrevoSender sender,
        List<BrevoRecipient> to,
        String subject,
        String htmlContent
) {}
