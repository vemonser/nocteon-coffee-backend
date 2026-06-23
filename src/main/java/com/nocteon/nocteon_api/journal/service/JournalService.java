package com.nocteon.nocteon_api.journal.service;

import java.util.List;
import java.util.ArrayList;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.notFound.JournalCategoryNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.JournalPostNotFoundException;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.journal.dto.request.JournalFilterRequest;
import com.nocteon.nocteon_api.journal.dto.request.JournalPostRequest;
import com.nocteon.nocteon_api.journal.dto.request.JournalPostTranslationRequest;
import com.nocteon.nocteon_api.journal.dto.response.JournalPostResponse;
import com.nocteon.nocteon_api.journal.entity.JournalCategory;
import com.nocteon.nocteon_api.journal.entity.JournalPost;
import com.nocteon.nocteon_api.journal.entity.JournalPostTranslation;
import com.nocteon.nocteon_api.journal.repository.JournalCategoryRepository;
import com.nocteon.nocteon_api.journal.repository.JournalPostRepository;
import com.nocteon.nocteon_api.journal.repository.JournalPostTranslationRepository;
import com.nocteon.nocteon_api.product.entity.Product;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class JournalService {

    private final JournalPostRepository postRepository;
    private final JournalPostTranslationRepository translationRepository;
    private final JournalCategoryRepository categoryRepository;
    private final LookupServiceHelper helper;

    
    public PageResponse<JournalPostResponse> getAll(JournalFilterRequest filter) {
        String language = LocaleContextHolder.getLocale().getLanguage();
        Page<JournalPost> page = postRepository.findAllPublic(
                language,
                filter.getSearch(),
                filter.getCategorySlug(),
                filter.getFeatured(),
                filter.toPageable());
        return PageResponse.of(page.map(p -> buildListResponse(p, language)));
    }

    public PageResponse<JournalPostResponse> getAllDashboard(JournalFilterRequest filter) {
        String language = LocaleContextHolder.getLocale().getLanguage();
        Page<JournalPost> page = postRepository.findAllDashboard(
                filter.getSearch(),
                filter.getCategorySlug(),
                filter.toPageable());
        return PageResponse.of(page.map(p -> buildListResponse(p, language)));
    }

    public JournalPostResponse getBySlug(String slug) {
        String language = LocaleContextHolder.getLocale().getLanguage();
        JournalPost post = postRepository.findBySlugAndLanguage(slug, language)
                .orElseThrow(JournalPostNotFoundException::new);
        return buildDetailResponse(post, language);
    }

    @Transactional
    public JournalPostResponse create(JournalPostRequest request, MultipartFile image) {
        helper.validateTranslations(request.getTranslations());

        JournalCategory category = categoryRepository.findBySlug(request.getCategorySlug())
                .orElseThrow(JournalCategoryNotFoundException::new);

        String title = request.getTranslations().stream()
                .filter(t -> t.getLanguage().equals("en"))
                .findFirst()
                .map(JournalPostTranslationRequest::getTitle)
                .orElseThrow(InvalidTranslationException::new);

        String slug = helper.generateUniqueSlug(title, postRepository::existsBySlug);
        String imageUrl = helper.uploadImage(image, "journal");

        JournalPost post = JournalPost.builder()
                .journalCategory(category)
                .slug(slug)
                .coverImageUrl(imageUrl)
                .featured(request.isFeatured())
                .publishedAt(request.getPublishedAt())
                .build();

        post = postRepository.save(post);

        final JournalPost savedPost = post;
        List<JournalPostTranslation> translations = new ArrayList<>();
        for (JournalPostTranslationRequest t : request.getTranslations()) {
            translations.add(JournalPostTranslation.builder()
                    .journalPost(savedPost)
                    .language(t.getLanguage())
                    .title(t.getTitle())
                    .excerpt(t.getExcerpt())
                    .content(t.getContent())
                    .metaTitle(t.getMetaTitle())
                    .metaDescription(t.getMetaDescription())
                    .build());
        }
        translationRepository.saveAll(translations);

        if (request.getProductSlugs() != null && !request.getProductSlugs().isEmpty()) {
            // ربط المقال بالمنتجات
        }

        log.info("Journal post created with slug: {}", slug);
        return getBySlug(slug);
    }

    @Transactional
    public JournalPostResponse uploadCover(String slug, MultipartFile file) {
        JournalPost post = postRepository.findBySlugAndLanguage(
                slug, LocaleContextHolder.getLocale().getLanguage())
                .orElseThrow(JournalPostNotFoundException::new);

        helper.deleteImageIfExists(post.getCoverImageUrl());
        post.setCoverImageUrl(helper.uploadImage(file, "journal"));
        postRepository.save(post);

        return getBySlug(slug);
    }

    @Transactional
    public void delete(String slug) {
        JournalPost post = postRepository.findBySlugAndLanguage(
                slug, LocaleContextHolder.getLocale().getLanguage())
                .orElseThrow(JournalPostNotFoundException::new);

        helper.deleteImageIfExists(post.getCoverImageUrl());
        post.softDelete();
        postRepository.save(post);
    }

    private JournalPostResponse buildListResponse(JournalPost post, String language) {
        JournalPostTranslation translation = translationRepository
                .findByJournalPostIdAndLanguage(post.getId(), language)
                .orElseGet(() -> translationRepository
                        .findByJournalPostId(post.getId())
                        .stream().findFirst().orElse(null));

        return JournalPostResponse.builder()
                .id(post.getId())
                .slug(post.getSlug())
                .categorySlug(post.getJournalCategory().getSlug())
                .coverImageUrl(post.getCoverImageUrl())
                .featured(post.isFeatured())
                .publishedAt(post.getPublishedAt())
                .title(translation != null ? translation.getTitle() : null)
                .excerpt(translation != null ? translation.getExcerpt() : null)
                .build();
    }

    private JournalPostResponse buildDetailResponse(JournalPost post, String language) {
        JournalPostTranslation translation = translationRepository
                .findByJournalPostIdAndLanguage(post.getId(), language)
                .orElseGet(() -> translationRepository
                        .findByJournalPostId(post.getId())
                        .stream().findFirst().orElse(null));

        List<String> relatedProductSlugs = post.getProducts().stream()
                .map(Product::getSlug)
                .toList();

        return JournalPostResponse.builder()
                .id(post.getId())
                .slug(post.getSlug())
                .categorySlug(post.getJournalCategory().getSlug())
                .coverImageUrl(post.getCoverImageUrl())
                .featured(post.isFeatured())
                .publishedAt(post.getPublishedAt())
                .title(translation != null ? translation.getTitle() : null)
                .excerpt(translation != null ? translation.getExcerpt() : null)
                .content(translation != null ? translation.getContent() : null)
                .metaTitle(translation != null ? translation.getMetaTitle() : null)
                .metaDescription(translation != null ? translation.getMetaDescription() : null)
                .relatedProductSlugs(relatedProductSlugs)
                .build();
    }
}