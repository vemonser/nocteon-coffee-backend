package com.nocteon.nocteon_api.journal.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.journal.entity.JournalCategory;
import com.nocteon.nocteon_api.journal.entity.JournalPost;
import com.nocteon.nocteon_api.journal.dto.request.JournalPostRequest;
import com.nocteon.nocteon_api.journal.dto.request.JournalPostTranslationRequest;
import com.nocteon.nocteon_api.journal.dto.response.JournalPostResponseDashboard;
import com.nocteon.nocteon_api.journal.entity.JournalPostTranslation;
import com.nocteon.nocteon_api.journal.enums.JournalPostStatus;
import com.nocteon.nocteon_api.journal.repository.JournalCategoryRepository;
import com.nocteon.nocteon_api.journal.repository.JournalPostRepository;
import com.nocteon.nocteon_api.journal.repository.JournalPostTranslationRepository;
import com.nocteon.nocteon_api.support.TestJwtKeys;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.ObjectMapper;

/**
 * FE-03: a future-dated PUBLISHED post is scheduled, not live.
 *
 * <p>{@code publishedAt} already existed, but the public queries only checked the status, so a
 * post dated tomorrow was readable today. These run the real JPQL against the database — a
 * mocked repository cannot prove a WHERE clause.
 */
@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class JournalScheduledPublishingIntegrationTest {

    @Autowired
    private JournalPostRepository postRepository;
    @Autowired
    private JournalPostTranslationRepository translationRepository;
    @Autowired
    private JournalCategoryRepository categoryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JournalService journalService;
    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void aFutureDatedPostIsAbsentPubliclyAndPresentInTheDashboard() {
        String slug = persistPost(Instant.now().plus(1, ChronoUnit.DAYS));
        Instant now = Instant.now();

        Page<JournalPost> publicPage = postRepository.findAllPublic(
                "en", null, null, null, now, PageRequest.of(0, 100));
        assertThat(publicPage.getContent())
                .extracting(JournalPost::getSlug)
                .doesNotContain(slug);

        assertThat(postRepository.findPublishedBySlugAndLanguage(slug, "en", now)).isEmpty();

        assertThat(postRepository.findBySlugForDashboard(slug)).isPresent();
        Page<JournalPost> staffPage = postRepository.findAllDashboard(
                null, null, PageRequest.of(0, 100));
        assertThat(staffPage.getContent())
                .extracting(JournalPost::getSlug)
                .contains(slug);
    }

    @Test
    void aPostDatedInThePastIsPublic() {
        String slug = persistPost(Instant.now().minus(1, ChronoUnit.HOURS));
        Instant now = Instant.now();

        Page<JournalPost> publicPage = postRepository.findAllPublic(
                "en", null, null, null, now, PageRequest.of(0, 100));
        assertThat(publicPage.getContent())
                .extracting(JournalPost::getSlug)
                .contains(slug);

        assertThat(postRepository.findPublishedBySlugAndLanguage(slug, "en", now)).isPresent();
    }

    /**
     * The dashboard detail builds its DTO in a transaction but Jackson serializes it after
     * the transaction has committed. A lazy collection handed straight to the DTO therefore
     * explodes as a 500 — this test runs outside a surrounding transaction so the session is
     * really closed before serialization.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void dashboardDetailSerializesAfterTheTransactionCloses() throws Exception {
        JournalPostResponseDashboard created = journalService.create(
                request(JournalPostStatus.PUBLISHED, Instant.now().plus(1, ChronoUnit.DAYS)),
                null,
                userRepository.findAll().get(0).getId());

        JournalPostResponseDashboard dto = journalService.getDashboardBySlug(created.getSlug());
        String json = objectMapper.writeValueAsString(dto);

        assertThat(json).contains("\"tags\"");
        postRepository.deleteById(dto.getId());
    }

    @Test
    void creatingAPostKeepsTheEditorsFutureDate() {
        JournalCategory category = categoryRepository.findAll().get(0);
        User author = userRepository.findAll().get(0);
        Instant tomorrow = Instant.now().plus(2, ChronoUnit.DAYS);

        JournalPostRequest request = new JournalPostRequest();
        request.setCategorySlug(category.getSlug());
        request.setStatus(JournalPostStatus.PUBLISHED);
        request.setPublishedAt(tomorrow);
        request.setTranslations(List.of(
                translation("en", "Scheduled from the form"),
                translation("ar", "مجدول من النموذج")));

        JournalPostResponseDashboard created = journalService.create(request, null, author.getId());

        assertThat(postRepository.findBySlugForDashboard(created.getSlug()).orElseThrow().getPublishedAt())
                .isEqualTo(tomorrow);
    }

    @Test
    void anArchivedPostIsNotPublicEvenWithAPastDate() {
        String slug = persistPost(Instant.now().minus(1, ChronoUnit.DAYS),
                JournalPostStatus.ARCHIVED, "en");
        Instant now = Instant.now();

        assertThat(postRepository.findAllPublic("en", null, null, null, now,
                PageRequest.of(0, 100)).getContent())
                .extracting(JournalPost::getSlug)
                .doesNotContain(slug);
        assertThat(postRepository.findPublishedBySlugAndLanguage(slug, "en", now)).isEmpty();
    }

    @Test
    void aPostWithoutATranslationForTheLanguageIsNotPublic() {
        String slug = persistPost(Instant.now().minus(1, ChronoUnit.DAYS),
                JournalPostStatus.PUBLISHED, "ar");
        Instant now = Instant.now();

        assertThat(postRepository.findAllPublic("en", null, null, null, now,
                PageRequest.of(0, 100)).getContent())
                .extracting(JournalPost::getSlug)
                .doesNotContain(slug);
        assertThat(postRepository.findAllPublic("ar", null, null, null, now,
                PageRequest.of(0, 100)).getContent())
                .extracting(JournalPost::getSlug)
                .contains(slug);
    }

    @Test
    void updatingWithoutADateKeepsTheStoredDate() {
        Instant tomorrow = Instant.now().plus(2, ChronoUnit.DAYS);
        JournalPostResponseDashboard created = journalService.create(
                request(JournalPostStatus.PUBLISHED, tomorrow),
                null,
                userRepository.findAll().get(0).getId());

        JournalPostRequest edit = request(JournalPostStatus.PUBLISHED, null);
        journalService.update(created.getSlug(), edit, null);

        assertThat(postRepository.findBySlugForDashboard(created.getSlug()).orElseThrow().getPublishedAt())
                .isEqualTo(tomorrow);
    }

    @Test
    void publishingADraftWithoutADateStampsNow() {
        JournalPostResponseDashboard created = journalService.create(
                request(JournalPostStatus.DRAFT, null),
                null,
                userRepository.findAll().get(0).getId());
        assertThat(postRepository.findBySlugForDashboard(created.getSlug()).orElseThrow().getPublishedAt())
                .isNull();

        journalService.update(created.getSlug(), request(JournalPostStatus.PUBLISHED, null), null);

        assertThat(postRepository.findBySlugForDashboard(created.getSlug()).orElseThrow().getPublishedAt())
                .isNotNull();
    }

    private JournalPostRequest request(JournalPostStatus status, Instant publishedAt) {
        JournalPostRequest request = new JournalPostRequest();
        request.setCategorySlug(categoryRepository.findAll().get(0).getSlug());
        request.setStatus(status);
        request.setPublishedAt(publishedAt);
        request.setTranslations(List.of(
                translation("en", "Scheduled test post"),
                translation("ar", "مجدول من النموذج")));
        return request;
    }

    private static JournalPostTranslationRequest translation(String language, String title) {
        JournalPostTranslationRequest translation = new JournalPostTranslationRequest();
        translation.setLanguage(language);
        translation.setTitle(title);
        translation.setContent("<p>Body</p>");
        return translation;
    }

    private String persistPost(Instant publishedAt) {
        return persistPost(publishedAt, JournalPostStatus.PUBLISHED, "en");
    }

    private String persistPost(Instant publishedAt, JournalPostStatus status, String language) {
        JournalCategory category = categoryRepository.findAll().get(0);
        User author = userRepository.findAll().get(0);
        String slug = "scheduled-" + UUID.randomUUID();

        JournalPost post = postRepository.save(JournalPost.builder()
                .slug(slug)
                .journalCategory(category)
                .author(author)
                .status(status)
                .publishedAt(publishedAt)
                .build());
        translationRepository.save(JournalPostTranslation.builder()
                .journalPost(post)
                .language(language)
                .title("Scheduled test post")
                .content("<p>Body</p>")
                .build());
        entityManager.flush();
        return slug;
    }
}
