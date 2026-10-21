package com.nocteon.nocteon_api.mail.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * The contact-reply email body.
 *
 * <p>It is rendered from a template in THIS repository, not from a template
 * in the Brevo dashboard: a Brevo template would be a manual step outside
 * the code, and there is no reason for the body of a reply to live there.
 */
@SpringBootTest(
        classes = NocteonApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ContactReplyEmailRendererTest {

    @Autowired
    private ContactReplyEmailRenderer renderer;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void theSubjectIsTheCustomersOwnSubjectWithARe() {
        Map<String, Object> params = new HashMap<>();
        params.put("originalSubject", "Question about shipping");

        assertThat(renderer.subject(params)).isEqualTo("Re: Question about shipping");
    }

    @Test
    void theBodyCarriesTheReplyAndQuotesTheOriginal() {
        Map<String, Object> params = new HashMap<>();
        params.put("recipientName", "Ahmed Hassan");
        params.put("originalSubject", "Question about shipping");
        params.put("originalMessage", "What are the rates to Alexandria?");
        params.put("replyContent", "Here are the rates: 75 EGP.");

        String html = renderer.render(params);

        assertThat(html).contains("Here are the rates: 75 EGP.");
        assertThat(html).contains("What are the rates to Alexandria?");
        assertThat(html).contains("Ahmed Hassan");
    }
}
