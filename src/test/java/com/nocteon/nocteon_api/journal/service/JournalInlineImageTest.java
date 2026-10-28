package com.nocteon.nocteon_api.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.common.service.HtmlSanitizer;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.common.service.RichTextImageService;
import com.nocteon.nocteon_api.journal.repository.JournalCategoryRepository;
import com.nocteon.nocteon_api.journal.repository.JournalPostRepository;
import com.nocteon.nocteon_api.journal.repository.JournalPostTranslationRepository;
import com.nocteon.nocteon_api.product.repository.ProductRepository;

/** The journal endpoint delegates to the shared rich-text upload with its own folder. */
@ExtendWith(MockitoExtension.class)
class JournalInlineImageTest {

    @Mock
    private JournalPostRepository postRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private JournalPostTranslationRepository translationRepository;
    @Mock
    private JournalCategoryRepository categoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LookupServiceHelper helper;
    @Mock
    private HtmlSanitizer htmlSanitizer;
    @Mock
    private RichTextImageService richTextImageService;

    @InjectMocks
    private JournalService service;

    @Test
    void delegatesToTheSharedUploadWithTheJournalFolder() {
        MockMultipartFile file = new MockMultipartFile("file", "bag.png", "image/png", new byte[] { 1 });
        when(richTextImageService.upload(file, "journal/inline")).thenReturn("https://cdn/bag.png");

        assertThat(service.uploadInlineImage(file)).isEqualTo("https://cdn/bag.png");
        verify(richTextImageService).upload(file, "journal/inline");
    }
}
