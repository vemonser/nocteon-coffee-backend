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

    private static JournalPostTranslationRequest translation(String language, String title) {
        JournalPostTranslationRequest translation = new JournalPostTranslationRequest();
        translation.setLanguage(language);
        translation.setTitle(title);
        translation.setContent("<p>Body</p>");
        return translation;
    }

    private String persistPost(Instant publishedAt) {
        JournalCategory category = categoryRepository.findAll().get(0);
        User author = userRepository.findAll().get(0);
        String slug = "scheduled-" + UUID.randomUUID();

        JournalPost post = postRepository.save(JournalPost.builder()
                .slug(slug)
                .journalCategory(category)
                .author(author)
                .status(JournalPostStatus.PUBLISHED)
                .publishedAt(publishedAt)
                .build());
        translationRepository.save(JournalPostTranslation.builder()
                .journalPost(post)
                .language("en")
                .title("Scheduled test post")
                .content("<p>Body</p>")
                .build());
        entityManager.flush();
        return slug;
    }
}
