package com.nocteon.nocteon_api.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.cloudinary.config.CloudinaryProperties;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidImageException;
import com.nocteon.nocteon_api.common.exception.upload.ImageSizeExceededException;
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
 * <p>The Content-Type header is client-supplied, so the service checks the magic bytes and an
 * explicit format allowlist: an SVG or HTML document must never be stored as an article image
 * (it would then be trusted by the renderer). Size is capped like the product media path.
 */
@ExtendWith(MockitoExtension.class)
class JournalInlineImageTest {

    private static final String UPLOADED =
            "https://res.cloudinary.com/nocteon-test/image/upload/v1/nocteon/journal/inline/bag.png";

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
        MockMultipartFile file = png("bag.png");
        when(helper.uploadMedia(file, "journal/inline", MediaType.IMAGE)).thenReturn(UPLOADED);

        assertThat(service.uploadInlineImage(file)).isEqualTo(UPLOADED);
    }

    /** The stored URL must survive FE-01's allowlist, or the image disappears silently on save. */
    @Test
    void theReturnedUrlSurvivesTheArticleSanitizer() {
        MockMultipartFile file = png("bag.png");
        when(helper.uploadMedia(file, "journal/inline", MediaType.IMAGE)).thenReturn(UPLOADED);
        CloudinaryProperties properties = new CloudinaryProperties();
        properties.setCloudName("nocteon-test");
        HtmlSanitizer sanitizer = new HtmlSanitizer(properties);

        String url = service.uploadInlineImage(file);
        String stored = sanitizer.sanitize("<p>Before</p><img src=\"" + url + "\" alt=\"bag\">");

        assertThat(stored).contains("<img src=\"" + url + "\"");
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
        MockMultipartFile text = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> service.uploadInlineImage(text))
                .isInstanceOf(InvalidImageException.class);
    }

    @Test
    void refusesAnImageContentTypeThatDoesNotMatchTheBytes() {
        MockMultipartFile spoofed = new MockMultipartFile(
                "file", "bag.png", "image/png", "<html>not an image</html>".getBytes());

        assertThatThrownBy(() -> service.uploadInlineImage(spoofed))
                .isInstanceOf(InvalidImageException.class);
    }

    @Test
    void refusesSvgEvenWithAnImageContentType() {
        MockMultipartFile svg = new MockMultipartFile(
                "file",
                "x.svg",
                "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes());

        assertThatThrownBy(() -> service.uploadInlineImage(svg))
                .isInstanceOf(InvalidImageException.class);
    }

    @Test
    void refusesAnImageLargerThanFiveMegabytes() {
        byte[] bytes = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(pngBytes(), 0, bytes, 0, 8);
        MockMultipartFile big = new MockMultipartFile("file", "big.png", "image/png", bytes);

        assertThatThrownBy(() -> service.uploadInlineImage(big))
                .isInstanceOf(ImageSizeExceededException.class);
    }

    private static MockMultipartFile png(String name) {
        return new MockMultipartFile("file", name, "image/png", pngBytes());
    }

    private static byte[] pngBytes() {
        byte[] signature = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
        byte[] body = new byte[40];
        Arrays.fill(body, (byte) 0);
        byte[] all = new byte[signature.length + body.length];
        System.arraycopy(signature, 0, all, 0, signature.length);
        System.arraycopy(body, 0, all, signature.length, body.length);
        return all;
    }
}
