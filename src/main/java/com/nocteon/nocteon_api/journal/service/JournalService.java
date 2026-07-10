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
import com.nocteon.nocteon_api.product.enums.MediaType;
import com.nocteon.nocteon_api.product.repository.ProductRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class JournalService {

        private final JournalPostRepository postRepository;
        private final ProductRepository productRepository;
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
                String imageUrl = helper.uploadMedia(image, "journal", MediaType.IMAGE);

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
                        List<Product> products = productRepository.findBySlugIn(request.getProductSlugs());
                        savedPost.setProducts(products);
                        postRepository.save(savedPost);
                }

                log.info("Journal post created with slug: {}", slug);
                return getBySlug(slug);
        }

        @Transactional
        public JournalPostResponse uploadCover(String slug, MultipartFile file) {
                JournalPost post = postRepository.findBySlugForDashboard(slug)
                                .orElseThrow(JournalPostNotFoundException::new);

                helper.deleteMediaIfExists(post.getCoverImageUrl());
                post.setCoverImageUrl(helper.uploadMedia(file, "journal", MediaType.IMAGE));
                postRepository.save(post);

                return getBySlug(slug);
        }

        @Transactional
        public JournalPostResponse update(String slug, JournalPostRequest request, MultipartFile image) {
                helper.validateTranslations(request.getTranslations());

                JournalPost post = postRepository.findBySlugForDashboard(slug)
                                .orElseThrow(JournalPostNotFoundException::new);

                JournalCategory category = categoryRepository.findBySlug(request.getCategorySlug())
                                .orElseThrow(JournalCategoryNotFoundException::new);

                post.setJournalCategory(category);
                post.setFeatured(request.isFeatured());
                post.setPublishedAt(request.getPublishedAt());

                if (image != null && !image.isEmpty()) {
                        helper.deleteMediaIfExists(post.getCoverImageUrl());
                        post.setCoverImageUrl(helper.uploadMedia(image, "journal", MediaType.IMAGE));
                }

                updateTranslations(post, request.getTranslations());
                updateProductLinks(post, request.getProductSlugs());

                postRepository.save(post);

                log.info("Journal post updated with slug: {}", slug);
                return getBySlug(slug);
        }

        private void updateTranslations(JournalPost post, List<JournalPostTranslationRequest> requests) {
                for (JournalPostTranslationRequest t : requests) {
                        JournalPostTranslation translation = translationRepository
                                        .findByJournalPostIdAndLanguage(post.getId(), t.getLanguage())
                                        .orElseGet(() -> JournalPostTranslation.builder()
                                                        .journalPost(post)
                                                        .language(t.getLanguage())
                                                        .build());

                        translation.setTitle(t.getTitle());
                        translation.setExcerpt(t.getExcerpt());
                        translation.setContent(t.getContent());
                        translation.setMetaTitle(t.getMetaTitle());
                        translation.setMetaDescription(t.getMetaDescription());

                        translationRepository.save(translation);
                }
        }

        private void updateProductLinks(JournalPost post, List<String> productSlugs) {
                List<Product> products = (productSlugs == null || productSlugs.isEmpty())
                                ? List.of()
                                : productRepository.findBySlugIn(productSlugs);

                post.setProducts(products);
        }

        @Transactional
        public void delete(String slug) {
                JournalPost post = postRepository.findBySlugForDashboard(slug)
                                .orElseThrow(JournalPostNotFoundException::new);

                helper.deleteMediaIfExists(post.getCoverImageUrl());
                post.softDelete();
                postRepository.save(post);
        }

        private JournalPostResponse buildListResponse(JournalPost post, String language) {
                JournalPostTranslation translation = post.getTranslations().stream()
                                .filter(t -> t.getLanguage().equals(language))
                                .findFirst()
                                .orElseGet(() -> post.getTranslations().stream().findFirst().orElse(null));

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
                JournalPostTranslation translation = post.getTranslations().stream()
                                .filter(t -> t.getLanguage().equals(language))
                                .findFirst()
                                .orElseGet(() -> post.getTranslations().stream().findFirst().orElse(null));

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