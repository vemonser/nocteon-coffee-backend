package com.nocteon.nocteon_api.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.cloudinary.config.CloudinaryProperties;
import com.nocteon.nocteon_api.common.service.HtmlSanitizer;
import com.nocteon.nocteon_api.common.service.RichTextImageService;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.journal.dto.request.JournalPostRequest;
import com.nocteon.nocteon_api.journal.dto.request.JournalPostTranslationRequest;
import com.nocteon.nocteon_api.journal.entity.JournalCategory;
import com.nocteon.nocteon_api.journal.entity.JournalPost;
import com.nocteon.nocteon_api.journal.entity.JournalPostTranslation;
import com.nocteon.nocteon_api.journal.enums.JournalPostStatus;
import com.nocteon.nocteon_api.journal.repository.JournalCategoryRepository;
import com.nocteon.nocteon_api.journal.repository.JournalPostRepository;
import com.nocteon.nocteon_api.journal.repository.JournalPostTranslationRepository;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.product.enums.MediaType;

/**
 * The journal body reaches the database only through the allowlist.
 *
 * <p>The sanitizer has its own unit test; this one proves the service actually calls it
 * on both write paths, by capturing the exact entity handed to
 * {@code translationRepository} — the value that would be persisted. A sanitizer that
 * exists but is not wired in is the failure mode being guarded against.
 */
@ExtendWith(MockitoExtension.class)
class JournalServiceSanitizationTest {

    private static final String PAYLOAD =
            "<h2>Brewing</h2>"
                    + "<p>Safe <strong>text</strong></p>"
                    + "<script>alert(1)</script>"
                    + "<img src=\"https://evil.example/beacon.png\" onerror=\"alert(1)\">"
                    + "<a href=\"javascript:alert(1)\">link</a>";

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

    private JournalService service;

    @BeforeEach
    void setUp() {
        CloudinaryProperties properties = new CloudinaryProperties();
        properties.setCloudName("nocteon-test");
        service = new JournalService(
                postRepository,
                productRepository,
                translationRepository,
                categoryRepository,
                userRepository,
                helper,
                new HtmlSanitizer(properties),
                org.mockito.Mockito.mock(RichTextImageService.class),
                org.mockito.Mockito.mock(com.nocteon.nocteon_api.audit.service.DomainAuditRecorder.class),
                org.mockito.Mockito.mock(com.nocteon.nocteon_api.common.service.TrashRules.class));
    }

    @Test
    @DisplayName("create persists the sanitized body, not the editor's raw output")
    void createStoresSanitizedContent() {
        JournalCategory category = JournalCategory.builder().slug("news").build();
        User author = User.builder().username("editor").build();

        when(categoryRepository.findBySlug("news")).thenReturn(Optional.of(category));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(helper.generateUniqueSlug(anyString(), any())).thenReturn("brewing");
        when(helper.uploadMedia(isNull(), eq("journal"), eq(MediaType.IMAGE))).thenReturn(null);

        AtomicReference<JournalPost> saved = new AtomicReference<>();
        when(postRepository.save(any(JournalPost.class))).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(translationRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(postRepository.findBySlugForDashboard("brewing"))
                .thenAnswer(invocation -> Optional.of(saved.get()));

        service.create(requestWithPayload(), null, 1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JournalPostTranslation>> captor = ArgumentCaptor.forClass(List.class);
        verify(translationRepository).saveAll(captor.capture());

        assertThat(captor.getValue()).hasSize(2);
        for (JournalPostTranslation stored : captor.getValue()) {
            assertThat(stored.getContent()).contains("<h2>Brewing</h2>", "<strong>text</strong>");
            assertThat(stored.getContent())
                    .doesNotContain("<script", "onerror", "javascript:", "evil.example");
        }
    }

    @Test
    @DisplayName("update persists the sanitized body for every language")
    void updateStoresSanitizedContent() {
        JournalCategory category = JournalCategory.builder().slug("news").build();
        JournalPost existing = JournalPost.builder()
                .slug("brewing")
                .journalCategory(category)
                .author(User.builder().username("editor").build())
                .status(JournalPostStatus.DRAFT)
                .build();

        when(postRepository.findBySlugForDashboard("brewing")).thenReturn(Optional.of(existing));
        when(categoryRepository.findBySlug("news")).thenReturn(Optional.of(category));

        service.update("brewing", requestWithPayload(), null);

        ArgumentCaptor<JournalPostTranslation> captor =
                ArgumentCaptor.forClass(JournalPostTranslation.class);
        verify(translationRepository, org.mockito.Mockito.times(2)).save(captor.capture());

        assertThat(captor.getAllValues()).hasSize(2);
        for (JournalPostTranslation stored : captor.getAllValues()) {
            assertThat(stored.getContent()).contains("<h2>Brewing</h2>");
            assertThat(stored.getContent())
                    .doesNotContain("<script", "onerror", "javascript:", "evil.example");
        }
    }

    private static JournalPostRequest requestWithPayload() {
        JournalPostRequest request = new JournalPostRequest();
        request.setCategorySlug("news");
        request.setStatus(JournalPostStatus.DRAFT);
        request.setTranslations(List.of(
                translation("en", "Brewing guide", PAYLOAD),
                translation("ar", "دليل التحضير", PAYLOAD)));
        return request;
    }

    private static JournalPostTranslationRequest translation(
            String language, String title, String content) {
        JournalPostTranslationRequest translation = new JournalPostTranslationRequest();
        translation.setLanguage(language);
        translation.setTitle(title);
        translation.setContent(content);
        return translation;
    }
}
