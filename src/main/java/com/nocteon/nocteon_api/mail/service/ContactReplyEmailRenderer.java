package com.nocteon.nocteon_api.mail.service;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import lombok.RequiredArgsConstructor;

/**
 * Renders the contact-reply email.
 *
 * <p>The body is rendered from a template in THIS repository, not from a
 * template in the Brevo dashboard: a Brevo template would be a manual step
 * outside the code, and the body of a reply has no reason to live there.
 */
@Component
@RequiredArgsConstructor
public class ContactReplyEmailRenderer {

    /** The template name — Thymeleaf resolves the prefix and the .html suffix. */
    private static final String TEMPLATE = "emails/contact-reply";

    private final TemplateEngine templateEngine;

    /**
     * The subject: the customer's OWN subject, prefixed. Their words, so it
     * reads correctly in whatever language they wrote in.
     */
    public String subject(Map<String, Object> params) {
        Object subject = params.get("originalSubject");
        return "Re: " + (subject == null ? "" : subject);
    }

    public String render(Map<String, Object> params) {
        Context context = new Context();
        params.forEach(context::setVariable);
        return templateEngine.process(TEMPLATE, context);
    }
}
