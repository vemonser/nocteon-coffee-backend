package com.nocteon.nocteon_api.mail.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.nocteon.nocteon_api.mail.dto.BrevoRawEmailRequest;

/**
 * A raw send carries a subject and an HTML body — and NO template id.
 *
 * <p>Brevo accepts EITHER a templateId + params, OR a subject + htmlContent,
 * not both. The contact reply uses the second form, so its body can live in
 * this repository instead of in the Brevo dashboard.
 */
class BrevoClientRawContentTest {

    @Test
    void aRawSendCarriesASubjectAndHtmlAndNoTemplateId() {
        BrevoClient client = new BrevoClient(null, null);

        BrevoRawEmailRequest request =
                client.buildRawRequest("ahmed@example.com", "Re: Question", "<p>Hello</p>");

        assertThat(request.subject()).isEqualTo("Re: Question");
        assertThat(request.htmlContent()).isEqualTo("<p>Hello</p>");
        assertThat(request.to()).hasSize(1);
        assertThat(request.to().get(0).email()).isEqualTo("ahmed@example.com");
    }
}
