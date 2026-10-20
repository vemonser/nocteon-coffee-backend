package com.nocteon.nocteon_api.contact;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.contact.dto.response.ContactResponse;
import com.nocteon.nocteon_api.contact.service.ContactService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * The email status on a contact message.
 *
 * <p>The seeded messages have never been answered, so their status is NONE.
 * That is the state the dashboard must show for a message nobody replied to
 * — not a blank, and not a false "sent".
 */
@SpringBootTest(
        classes = NocteonApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ContactEmailStatusIntegrationTest {

    @Autowired
    private ContactService contactService;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void aMessageThatWasNeverAnsweredCarriesNoEmailStatus() {
        PageResponse<ContactResponse> page = contactService.getAll(new BaseFilterRequest());

        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent())
                .allSatisfy(m -> assertThat(m.getEmailStatus()).isNotNull());
        assertThat(page.getContent())
                .filteredOn(m -> m.getReplyContent() == null)
                .allSatisfy(m -> assertThat(m.getEmailStatus()).isEqualTo("NONE"));
    }
}
