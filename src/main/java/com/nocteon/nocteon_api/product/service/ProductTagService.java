package com.nocteon.nocteon_api.product.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.invalid.ProductTagInUseException;
import com.nocteon.nocteon_api.common.exception.notFound.ProductTagNotFoundException;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.common.util.SlugUtils;
import com.nocteon.nocteon_api.journal.dto.response.TranslationResponseDashboard;
import com.nocteon.nocteon_api.product.dto.request.ProductTagRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductTagTranslationRequest;
import com.nocteon.nocteon_api.product.dto.response.ProductTagResponseDashboard;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductTag;
import com.nocteon.nocteon_api.product.entity.ProductTagTranslation;
import com.nocteon.nocteon_api.product.repository.ProductTagRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * FE-10: merchandising tags a product can carry.
 *
 * <p>Same contract as journal categories: the slug is generated once from the English name and
 * never rewritten, renames only touch translated names, and a tag that products still point at
 * is refused rather than deleted out from under them.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProductTagService {

    private final ProductTagRepository tagRepository;
    private final LookupServiceHelper helper;

    @Transactional(readOnly = true)
    public List<ProductTagResponseDashboard> getTags() {
        return tagRepository.findAllWithTranslations().stream()
                .map(ProductTagService::toResponse)
                .toList();
    }

    @Transactional
    public ProductTagResponseDashboard createTag(ProductTagRequest request) {
        helper.validateTranslations(request.getTranslations());
        String englishName = request.getTranslations().stream()
                .filter(t -> t.getLanguage().equals("en"))
                .findFirst()
                .map(ProductTagTranslationRequest::getName)
                .orElseThrow(InvalidTranslationException::new);

        // The slug column is unique across trashed rows too: include them, and lock the base slug
        // so two admins creating the same name cannot both pass the check.
        String baseSlug = SlugUtils.toSlug(englishName);
        helper.acquireNameLock("product-tag", "slug", baseSlug);
        String slug = helper.generateUniqueSlug(
                englishName, tagRepository::existsBySlugIncludingDeleted);

        ProductTag tag = ProductTag.builder().slug(slug).build();
        for (ProductTagTranslationRequest t : request.getTranslations()) {
            tag.getTranslations().add(ProductTagTranslation.builder()
                    .tag(tag)
                    .language(t.getLanguage())
                    .name(t.getName().trim())
                    .build());
        }
        tagRepository.save(tag);
        log.info("Product tag created: {}", slug);
        return toResponse(tag);
    }

    @Transactional
    public ProductTagResponseDashboard renameTag(Long id, ProductTagRequest request) {
        helper.validateTranslations(request.getTranslations());
        ProductTag tag = tagRepository.findById(id)
                .orElseThrow(ProductTagNotFoundException::new);

        for (ProductTagTranslationRequest t : request.getTranslations()) {
            ProductTagTranslation translation = tag.getTranslations().stream()
                    .filter(existing -> existing.getLanguage().equals(t.getLanguage()))
                    .findFirst()
                    .orElseGet(() -> {
                        ProductTagTranslation created = ProductTagTranslation.builder()
                                .tag(tag)
                                .language(t.getLanguage())
                                .build();
                        tag.getTranslations().add(created);
                        return created;
                    });
            translation.setName(t.getName().trim());
        }
        tagRepository.save(tag);
        log.info("Product tag renamed: {}", tag.getSlug());
        return toResponse(tag);
    }

    /**
     * Removes a tag, refusing while products still carry it.
     *
     * <p>The count includes trashed products: the link outlives a product's soft delete, so
     * removing the tag would leave a restored product pointing at a row that no longer exists.
     */
    @Transactional
    public void deleteTag(Long id) {
        ProductTag tag = tagRepository.findById(id)
                .orElseThrow(ProductTagNotFoundException::new);
        long products = tagRepository.countLinkedProducts(id);
        if (products > 0) {
            throw new ProductTagInUseException(products);
        }
        tag.softDelete();
        tagRepository.save(tag);
        log.info("Product tag deleted: {}", tag.getSlug());
    }

    /**
     * Sets the product's tags to exactly {@code slugs}.
     *
     * <p>Mutates the loaded collection instead of assigning a new one: Hibernate merges the entity
     * and would call {@code clear()} on the replacement, which fails for an immutable set.
     */
    @Transactional
    public void replaceTags(Product product, List<String> slugs) {
        Set<ProductTag> resolved = resolve(slugs);
        if (product.getTags() == null) {
            product.setTags(new HashSet<>());
        }
        product.getTags().clear();
        product.getTags().addAll(resolved);
    }

    /**
     * A slug the request names but the catalogue does not have is a client error, not a silently
     * dropped tag: the admin typed it, and a product saved without it looks successful.
     */
    private Set<ProductTag> resolve(List<String> slugs) {
        List<String> wanted = slugs == null
                ? List.of()
                : slugs.stream().filter(StringUtils::hasText).distinct().toList();
        if (wanted.isEmpty()) {
            return new HashSet<>();
        }
        List<ProductTag> found = tagRepository.findAllBySlugIn(wanted);
        if (found.size() != wanted.size()) {
            Set<String> foundSlugs = new HashSet<>();
            found.forEach(tag -> foundSlugs.add(tag.getSlug()));
            wanted.stream()
                    .filter(slug -> !foundSlugs.contains(slug))
                    .findFirst()
                    .ifPresent(missing -> log.warn("Unknown product tag requested: {}", missing));
            throw new ProductTagNotFoundException();
        }
        return new HashSet<>(found);
    }

    private static ProductTagResponseDashboard toResponse(ProductTag tag) {
        return ProductTagResponseDashboard.builder()
                .id(tag.getId())
                .slug(tag.getSlug())
                .createdAt(tag.getCreatedAt())
                .translations(translationsOf(tag))
                .build();
    }

    private static List<TranslationResponseDashboard> translationsOf(ProductTag tag) {
        Collection<ProductTagTranslation> translations = tag.getTranslations();
        return translations.stream()
                .sorted((a, b) -> a.getLanguage().compareTo(b.getLanguage()))
                .map(t -> TranslationResponseDashboard.builder()
                        .language(t.getLanguage())
                        .title(t.getName())
                        .build())
                .toList();
    }
}
