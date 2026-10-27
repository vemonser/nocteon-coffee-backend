package com.nocteon.nocteon_api.journal.service;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.notFound.JournalCategoryNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.JournalPostNotFoundException;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.common.transaction.AfterCommit;
import com.nocteon.nocteon_api.journal.dto.request.JournalFilterRequest;
import com.nocteon.nocteon_api.journal.dto.request.JournalPostRequest;
import com.nocteon.nocteon_api.journal.dto.request.JournalPostTranslationRequest;
import com.nocteon.nocteon_api.journal.dto.response.JournalCategoryResponseDashboard;
import com.nocteon.nocteon_api.journal.dto.response.JournalPostResponse;
import com.nocteon.nocteon_api.journal.dto.response.JournalPostResponseDashboard;
import com.nocteon.nocteon_api.journal.dto.response.TranslationResponseDashboard;
import com.nocteon.nocteon_api.journal.entity.JournalCategory;
import com.nocteon.nocteon_api.journal.entity.JournalPost;
import com.nocteon.nocteon_api.journal.entity.JournalPostTranslation;
import com.nocteon.nocteon_api.journal.enums.JournalPostStatus;
import com.nocteon.nocteon_api.journal.repository.JournalCategoryRepository;
import com.nocteon.nocteon_api.journal.repository.JournalPostRepository;
import com.nocteon.nocteon_api.journal.repository.JournalPostTranslationRepository;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.enums.MediaType;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.common.repository.SearchPatterns;

import org.springframework.transaction.annotation.Transactional;
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
        private final UserRepository userRepository;
        private final LookupServiceHelper helper;

        private static final int WORDS_PER_MINUTE = 200;

        public PageResponse<JournalPostResponse> getAll(JournalFilterRequest filter) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                Page<JournalPost> page = postRepository.findAllPublic(
                                language,
                                SearchPatterns.like(filter.getSearch()),
                                filter.getCategorySlug(),
                                filter.getFeatured(),
                                filter.toPageable());
                return PageResponse.of(page.map(p -> buildListResponse(p, language)));
        }

        @Transactional(readOnly = true)
        public PageResponse<JournalPostResponse> getAllDashboard(JournalFilterRequest filter) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                Page<JournalPost> page = postRepository.findAllDashboard(
                                SearchPatterns.like(filter.getSearch()),
                                filter.getCategorySlug(),
                                filter.toPageable());
                return PageResponse.of(page.map(p -> buildListResponse(p, language)));
        }

        public JournalPostResponse getBySlug(String slug) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                // findPublishedBySlugAndLanguage, not findBySlugForDashboard: this backs the
                // anonymous /api/journal/{slug} route, so an unpublished post must resolve to
                // 404 rather than returning its body.
                JournalPost post = postRepository.findPublishedBySlugAndLanguage(slug, language)
                                .orElseThrow(JournalPostNotFoundException::new);
                return buildDetailResponse(post, language);
        }

        @Transactional(readOnly = true)
        public JournalPostResponseDashboard getDashboardBySlug(String slug) {
                JournalPost post = postRepository.findBySlugForDashboard(slug)
                                .orElseThrow(JournalPostNotFoundException::new);
                return buildDashboardResponse(post);
        }

        private JournalPostResponseDashboard buildDashboardResponse(JournalPost post) {
                List<TranslationResponseDashboard> translations = post.getTranslations().stream()
                                .map(t -> TranslationResponseDashboard.builder()
                                                .language(t.getLanguage())
                                                .title(t.getTitle())
                                                .excerpt(t.getExcerpt())
                                                .content(t.getContent())
                                                .metaTitle(t.getMetaTitle())
                                                .metaDescription(t.getMetaDescription())
                                                .build())
                                .toList();

                List<String> relatedProductSlugs = post.getProducts().stream()
                                .map(Product::getSlug)
                                .toList();

                return JournalPostResponseDashboard.builder()
                                .id(post.getId())
                                .slug(post.getSlug())
                                .categorySlug(post.getJournalCategory().getSlug())
                                .authorUsername(post.getAuthor().getUsername())
                                .coverImageUrl(post.getCoverImageUrl())
                                .featured(post.isFeatured())
                                .status(post.getStatus())
                                .readingTimeMinutes(post.getReadingTimeMinutes())
                                .publishedAt(post.getPublishedAt())
                                .tags(post.getTags())
                                .relatedProductSlugs(relatedProductSlugs)
                                .createdAt(post.getCreatedAt())
                                .translations(translations)
                                .build();
        }

        @Transactional
        public JournalPostResponseDashboard create(JournalPostRequest request, MultipartFile image, Long authorId) {
                helper.validateTranslations(request.getTranslations());

                JournalCategory category = categoryRepository.findBySlug(request.getCategorySlug())
                                .orElseThrow(JournalCategoryNotFoundException::new);

                User author = userRepository.findById(authorId).orElseThrow();

                String title = request.getTranslations().stream()
                                .filter(t -> t.getLanguage().equals("en"))
                                .findFirst()
                                .map(JournalPostTranslationRequest::getTitle)
                                .orElseThrow(InvalidTranslationException::new);

                String slug = helper.generateUniqueSlug(title, postRepository::existsBySlug);
                String imageUrl = helper.uploadMedia(image, "journal", MediaType.IMAGE);

                int readingTime = calculateReadingTime(request.getTranslations());

                JournalPost post = JournalPost.builder()
                                .journalCategory(category)
                                .author(author)
                                .slug(slug)
                                .coverImageUrl(imageUrl)
                                .featured(request.isFeatured())
                                .status(request.getStatus())
                                .readingTimeMinutes(readingTime)
                                .tags(request.getTags() != null ? Set.copyOf(request.getTags()) : Set.of())
                                .build();

                if (request.getStatus() == JournalPostStatus.PUBLISHED && post.getPublishedAt() == null) {
                        post.setPublishedAt(java.time.Instant.now());
                }

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
                return getDashboardBySlug(slug);
        }

        @Transactional
        public JournalPostResponseDashboard uploadCover(String slug, MultipartFile file) {
                JournalPost post = postRepository.findBySlugForDashboard(slug)
                                .orElseThrow(JournalPostNotFoundException::new);

                helper.deleteMediaIfExists(post.getCoverImageUrl());
                post.setCoverImageUrl(helper.uploadMedia(file, "journal", MediaType.IMAGE));
                postRepository.save(post);

                return getDashboardBySlug(slug);
        }

        @Transactional
        public JournalPostResponseDashboard update(String slug, JournalPostRequest request, MultipartFile image) {
                helper.validateTranslations(request.getTranslations());

                JournalPost post = postRepository.findBySlugForDashboard(slug)
                                .orElseThrow(JournalPostNotFoundException::new);

                JournalCategory category = categoryRepository.findBySlug(request.getCategorySlug())
                                .orElseThrow(JournalCategoryNotFoundException::new);

                post.setJournalCategory(category);
                post.setFeatured(request.isFeatured());
                post.setStatus(request.getStatus());
                post.setReadingTimeMinutes(calculateReadingTime(request.getTranslations()));
                post.setTags(request.getTags() != null ? Set.copyOf(request.getTags()) : Set.of());

                if (request.getStatus() == JournalPostStatus.PUBLISHED && post.getPublishedAt() == null) {
                        post.setPublishedAt(java.time.Instant.now());
                }

                if (image != null && !image.isEmpty()) {
                        helper.deleteMediaIfExists(post.getCoverImageUrl());
                        post.setCoverImageUrl(helper.uploadMedia(image, "journal", MediaType.IMAGE));
                }

                updateTranslations(post, request.getTranslations());
                updateProductLinks(post, request.getProductSlugs());

                postRepository.save(post);

                log.info("Journal post updated with slug: {}", slug);
                return getDashboardBySlug(slug);
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

                post.softDelete();
                postRepository.save(post);
        }

        private int calculateReadingTime(List<JournalPostTranslationRequest> translations) {
                if (translations == null || translations.isEmpty()) {
                        return 0;
                }
                int totalWords = 0;
                for (JournalPostTranslationRequest t : translations) {
                        if (t.getContent() != null && !t.getContent().isBlank()) {
                                totalWords += t.getContent().split("\\s+").length;
                        }
                }
                return Math.max(1, (int) Math.ceil((double) totalWords / WORDS_PER_MINUTE));
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
                                .authorUsername(post.getAuthor().getUsername())
                                .coverImageUrl(post.getCoverImageUrl())
                                .featured(post.isFeatured())
                                .status(post.getStatus())
                                .readingTimeMinutes(post.getReadingTimeMinutes())
                                .publishedAt(post.getPublishedAt())
                                .tags(post.getTags())
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
                                .authorUsername(post.getAuthor().getUsername())
                                .coverImageUrl(post.getCoverImageUrl())
                                .featured(post.isFeatured())
                                .status(post.getStatus())
                                .readingTimeMinutes(post.getReadingTimeMinutes())
                                .publishedAt(post.getPublishedAt())
                                .tags(post.getTags())
                                .title(translation != null ? translation.getTitle() : null)
                                .excerpt(translation != null ? translation.getExcerpt() : null)
                                .content(translation != null ? translation.getContent() : null)
                                .metaTitle(translation != null ? translation.getMetaTitle() : null)
                                .metaDescription(translation != null ? translation.getMetaDescription() : null)
                                .relatedProductSlugs(relatedProductSlugs)
                                .build();
        }

        // ===== Trashed posts and categories =====

        /**
         * Trashed posts, paged.
         *
         * <p>Takes {@link LookupFilterRequest} rather than the live dashboard's
         * {@link JournalFilterRequest} on purpose: a trash bin has no category, featured or
         * status facet, because a trashed row's category may itself be trashed and its status is
         * whatever it was when deleted. Reusing the live filter would imply filters the deleted
         * query does not honour.
         */
        public PageResponse<JournalPostResponseDashboard> getAllDeleted(LookupFilterRequest filter) {
                String search = SearchPatterns.like(filter.getSearch());
                int pageNum = filter.getPage();
                int size = filter.getSize();
                String sort = filter.getSort();
                String direction = filter.getDirection();

                List<Object[]> rawResults = postRepository.findDeletedBySearchNative(search);

                // One row per (post, language): collapse by id so each response carries all of its
                // translations, the shape the other trash-bin listings in this codebase return.
                Map<Long, JournalPostResponseDashboard> byId = new LinkedHashMap<>();
                for (Object[] row : rawResults) {
                        JournalPostResponseDashboard mapped = mapToJournalPostResponseDashboard(row);
                        byId.computeIfAbsent(mapped.getId(), id -> mapped)
                                        .getTranslations()
                                        .add(JournalPostTranslationResponse(row));
                }

                List<JournalPostResponseDashboard> allResults = new ArrayList<>(byId.values());

                sortPosts(allResults, sort, direction);

                int fromIndex = pageNum * size;
                int toIndex = Math.min(fromIndex + size, allResults.size());
                // subList of an ArrayList view is fine, but an empty range still has to be legal:
                // fromIndex > size means the caller paged past the end.
                if (fromIndex > allResults.size()) {
                        fromIndex = allResults.size();
                }
                List<JournalPostResponseDashboard> pagedResults = allResults.subList(fromIndex, toIndex);

                Page<JournalPostResponseDashboard> page = new org.springframework.data.domain.PageImpl<>(
                                pagedResults,
                                org.springframework.data.domain.PageRequest.of(pageNum, size),
                                allResults.size());
                return PageResponse.of(page);
        }

        private void sortPosts(List<JournalPostResponseDashboard> posts, String sort, String direction) {
                if (sort == null) {
                        return;
                }
                posts.sort((a, b) -> {
                        int result = switch (sort) {
                                case "slug" -> a.getSlug().compareToIgnoreCase(b.getSlug());
                                case "status" -> a.getStatus().compareTo(b.getStatus());
                                case "createdAt" -> a.getCreatedAt().compareTo(b.getCreatedAt());
                                default -> 0;
                        };
                        return direction.equalsIgnoreCase("desc") ? -result : result;
                });
        }

        private JournalPostResponseDashboard mapToJournalPostResponseDashboard(Object[] row) {
                return JournalPostResponseDashboard.builder()
                                .id(((Number) row[0]).longValue())
                                .slug((String) row[1])
                                .coverImageUrl((String) row[2])
                                .featured((Boolean) row[3])
                                // {@code status} arrives as the raw VARCHAR, not the mapped enum:
                                // Hibernate applies @Enumerated only to JPQL and derived queries, so
                                // a native query's Object[] holds a String. Casting straight to
                                // JournalPostStatus throws ClassCastException. A mocked unit test
                                // cannot reproduce this (the cast only fails on a real JDBC row),
                                // so it must be covered by an integration test against a real DB.
                                .status(JournalPostStatus.valueOf((String) row[4]))
                                .createdAt((Instant) row[5])
                                .translations(new ArrayList<>())
                                .build();
        }

        private TranslationResponseDashboard JournalPostTranslationResponse(Object[] row) {
                return TranslationResponseDashboard.builder()
                                .language((String) row[6])
                                .title((String) row[7])
                                .build();
        }

        /**
         * Un-delete a post.
         *
         * <p>The lookup goes through {@code findDeletedById}, so an id that is live or unknown
         * raises {@link JournalPostNotFoundException} rather than silently succeeding — a restore
         * that reports success for a row it never found is worse than an error.
         */
        @Transactional
        public void restore(Long id) {
                JournalPost post = postRepository.findDeletedById(id)
                                .orElseThrow(JournalPostNotFoundException::new);
                post.setDeletedAt(null);
                postRepository.save(post);
                log.info("Journal post restored: {}", post.getSlug());
        }

        /**
         * Trashed categories, paged. Same collapse-by-id shape as the post listing.
         */
        public PageResponse<JournalCategoryResponseDashboard> getAllDeletedCategories(
                        LookupFilterRequest filter) {
                String search = SearchPatterns.like(filter.getSearch());
                int pageNum = filter.getPage();
                int size = filter.getSize();
                String sort = filter.getSort();
                String direction = filter.getDirection();

                List<Object[]> rawResults = categoryRepository.findDeletedBySearchNative(search);

                Map<Long, JournalCategoryResponseDashboard> byId = new LinkedHashMap<>();
                for (Object[] row : rawResults) {
                        JournalCategoryResponseDashboard mapped = mapToJournalCategoryResponseDashboard(row);
                        byId.computeIfAbsent(mapped.getId(), id -> mapped)
                                        .getTranslations()
                                        .add(TranslationResponseDashboard.builder()
                                                        .language((String) row[3])
                                                        .title((String) row[4])
                                                        .build());
                }

                List<JournalCategoryResponseDashboard> allResults = new ArrayList<>(byId.values());

                if (sort != null) {
                        allResults.sort((a, b) -> {
                                int result = switch (sort) {
                                        case "slug" -> a.getSlug().compareToIgnoreCase(b.getSlug());
                                        case "createdAt" -> a.getCreatedAt().compareTo(b.getCreatedAt());
                                        default -> 0;
                                };
                                return direction.equalsIgnoreCase("desc") ? -result : result;
                        });
                }

                int fromIndex = pageNum * size;
                int toIndex = Math.min(fromIndex + size, allResults.size());
                if (fromIndex > allResults.size()) {
                        fromIndex = allResults.size();
                }
                List<JournalCategoryResponseDashboard> pagedResults = allResults.subList(fromIndex, toIndex);

                Page<JournalCategoryResponseDashboard> page = new org.springframework.data.domain.PageImpl<>(
                                pagedResults,
                                org.springframework.data.domain.PageRequest.of(pageNum, size),
                                allResults.size());
                return PageResponse.of(page);
        }

        private JournalCategoryResponseDashboard mapToJournalCategoryResponseDashboard(Object[] row) {
                return JournalCategoryResponseDashboard.builder()
                                .id(((Number) row[0]).longValue())
                                .slug((String) row[1])
                                .createdAt((Instant) row[2])
                                .translations(new ArrayList<>())
                                .build();
        }

        /**
         * Un-delete a category. Same contract as {@link #restore(Long)} — an id that is live or
         * unknown is a not-found, not a no-op.
         */
        @Transactional
        public void restoreCategory(Long id) {
                JournalCategory category = categoryRepository.findDeletedById(id)
                                .orElseThrow(JournalCategoryNotFoundException::new);
                category.setDeletedAt(null);
                categoryRepository.save(category);
                log.info("Journal category restored: {}", category.getSlug());
        }

        /**
         * Permanently deletes a trashed post: removes its cover from Cloudinary, then the row
         * itself (translations go with it through the cascade).
         *
         * <p>Trash-only: reads through {@code findDeletedById}, so a live or unknown id raises
         * {@link JournalPostNotFoundException} instead of deleting anything. Soft-delete first —
         * this never touches live rows.
         */
        @Transactional
        public void permanentDelete(Long id) {
                JournalPost post = postRepository.findDeletedById(id)
                                .orElseThrow(JournalPostNotFoundException::new);
                postRepository.delete(post);
                postRepository.flush();
                String imageUrl = post.getCoverImageUrl();
                String slug = post.getSlug();
                AfterCommit.run(() -> {
                    try {
                        helper.deleteMediaIfExists(imageUrl);
                    } catch (Exception e) {
                        log.warn("Cloudinary delete failed for journal post {} ({}): {}", slug, imageUrl, e.getMessage());
                    }
                });
                log.info("Journal post permanently deleted: {}", slug);
        }

        /**
         * Permanently deletes a trashed journal category, row and translations.
         *
         * <p>Trash-only, like {@link #permanentDelete(Long)}: a live or unknown id is a not-found.
         * A category still referenced by posts cannot be removed — the database rejects it and the
         * request surfaces as a 409.
         */
        @Transactional
        public void permanentDeleteCategory(Long id) {
                JournalCategory category = categoryRepository.findDeletedById(id)
                                .orElseThrow(JournalCategoryNotFoundException::new);
                categoryRepository.delete(category);
                log.info("Journal category permanently deleted: {}", category.getSlug());
        }
}
