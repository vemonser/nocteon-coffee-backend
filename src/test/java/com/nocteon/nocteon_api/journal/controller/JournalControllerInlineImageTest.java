package com.nocteon.nocteon_api.journal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.journal.service.JournalService;

import java.util.Map;

/** The inline-image endpoint returns the URL the editor must insert. */
@ExtendWith(MockitoExtension.class)
class JournalControllerInlineImageTest {

    @Mock
    private JournalService journalService;

    @InjectMocks
    private JournalController controller;

    @Test
    void returnsTheUploadedUrl() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "bag.png", "image/png", new byte[] { 1, 2, 3 });
        String url = "https://res.cloudinary.com/nocteon-test/image/upload/v1/nocteon/journal/inline/bag.png";
        when(journalService.uploadInlineImage(file)).thenReturn(url);

        ResponseEntity<ApiResponse<Map<String, String>>> response = controller.uploadInlineImage(file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).containsEntry("url", url);
        verify(journalService).uploadInlineImage(file);
    }
}
