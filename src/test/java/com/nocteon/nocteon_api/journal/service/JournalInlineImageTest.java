package com.nocteon.nocteon_api.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidImageException;
import com.nocteon.nocteon_api.common.service.HtmlSanitizer;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.journal.repository.JournalCategoryRepository;
import com.nocteon.nocteon_api.journal.repository.JournalPostRepository;
import com.nocteon.nocteon_api.journal.repository.JournalPostTranslationRepository;
import com.nocteon.nocteon_api.product.enums.MediaType;
import com.nocteon.nocteon_api.product.repository.ProductRepository;

/**
 * FE-02: the toolbar's image button uploads through the media pipeline.
 *
 * <p>Only images are accepted — an arbitrary file posted to this endpoint would otherwise be
 * stored on the CDN and then embedded in an article body.
 */
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

    @InjectMocks
    private JournalService service;

    @Test
    void uploadsAnImageIntoTheJournalFolderAndReturnsItsUrl() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "bag.png", "image/png", new byte[] { 1, 2, 3 });
        String url = "https://res.cloudinary.com/nocteon-test/image/upload/v1/nocteon/journal/inline/bag.png";
        when(helper.uploadMedia(file, "journal/inline", MediaType.IMAGE)).thenReturn(url);

        assertThat(service.uploadInlineImage(file)).isEqualTo(url);
    }

    @Test
    void refusesAnEmptyFile() {
        assertThatThrownBy(() -> service.uploadInlineImage(null))
                .isInstanceOf(InvalidImageException.class);
        assertThatThrownBy(() -> service.uploadInlineImage(
                new MockMultipartFile("file", "empty.png", "image/png", new byte[0])))
                .isInstanceOf(InvalidImageException.class);
    }

    @Test
    void refusesANonImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> service.uploadInlineImage(file))
                .isInstanceOf(InvalidImageException.class);
    }
}
