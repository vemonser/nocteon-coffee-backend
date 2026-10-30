package com.nocteon.nocteon_api.product.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.audit.service.DomainAuditRecorder;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.notFound.ProductTagNotFoundException;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.common.service.TrashRules;
import com.nocteon.nocteon_api.common.service.TrashRules.Reference;
import com.nocteon.nocteon_api.common.service.TrashRules.Table;
import com.nocteon.nocteon_api.common.util.SlugUtils;
import com.nocteon.nocteon_api.journal.dto.response.TranslationResponseDashboard;
import com.nocteon.nocteon_api.product.dto.request.ProductTagRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductTagTranslationRequest;
import com.nocteon.nocteon_api.product.dto.response.ProductTagResponseDashboard;
import com.nocteon.nocteon_api.product.dto.response.summary.ProductTagSummary;
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

    /** The entity_type every row for this catalogue records. */
    private static final String ENTITY = "ProductTag";

    private final ProductTagRepository tagRepository;
    private final LookupServiceHelper helper;
    private final DomainAuditRecorder domainAudit;
    private final TrashRules trashRules;

    @Transactional(readOnly = true)
    public List<ProductTagResponseDashboard> getTags() {
        return tagRepository.findAllWithTranslations().stream()
                .map(ProductTagService::toResponse)
                .toList();
    }

    /**
     * FE-STOREFRONT: the tags the public catalogue can filter by, already localized. The dashboard
     * endpoint returns the full translation set; this one resolves the name so the storefront does
     * not have to.
     */
    @Transactional(readOnly = true)
    public List<ProductTagSummary> getTagSummaries(String language) {
        return tagRepository.findAllWithTranslations().stream()
                .map(tag -> ProductTagSummary.builder()
                        .slug(tag.getSlug())
                        .name(tag.getTranslations().stream()
                                .filter(t -> t.getLanguage().equals(language))
                                .map(ProductTagTranslation::getName)
                                .findFirst()
                                .orElseGet(() -> tag.getTranslations().stream()
                                        .filter(t -> t.getLanguage().equals("en"))
                                        .map(ProductTagTranslation::getName)
                                        .findFirst()
                                        .orElse(tag.getSlug())))
                        .build())
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

        domainAudit.created(AuditAction.PRODUCT_TAG_CREATE, ENTITY, tag.getId(), slug, tagFields(tag));

        log.info("Product tag created: {}", slug);
        return toResponse(tag);
    }

    @Transactional
    public ProductTagResponseDashboard renameTag(Long id, ProductTagRequest request) {
        helper.validateTranslations(request.getTranslations());
        ProductTag tag = tagRepository.findById(id)
                .orElseThrow(ProductTagNotFoundException::new);

        Map<String, Object> before = tagFields(tag);

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

        domainAudit.updated(AuditAction.PRODUCT_TAG_UPDATE, ENTITY, tag.getId(), tag.getSlug(),
                before, tagFields(tag));

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
        trashRules.refuseIfReferenced(Reference.PRODUCT_TAG_LINK, id);
        tag.softDelete();
        tagRepository.save(tag);

        domainAudit.softDeleted(AuditAction.PRODUCT_TAG_DELETE, ENTITY, tag.getId(), tag.getSlug(),
                tag.getDeletedAt());

        log.info("Product tag deleted: {}", tag.getSlug());
    }

    /** Trashed tags, for the trash view. */
    @Transactional(readOnly = true)
    public List<ProductTagResponseDashboard> getDeletedTags() {
        return tagRepository.findAllDeletedNative().stream()
                .map(ProductTagService::toResponse)
                .toList();
    }

    /** Un-delete a tag. Only a trashed id resolves, so a live id is a 404 like an unknown one. */
    @Transactional
    public void restore(Long id) {
        ProductTag tag = tagRepository.findDeletedById(id)
                .orElseThrow(ProductTagNotFoundException::new);
        tag.setDeletedAt(null);
        tagRepository.save(tag);
        log.info("Product tag restored: {}", tag.getSlug());
    }

    /**
     * Permanently deletes a trashed tag: the row, its translations and its product links.
     *
     * <p>Trash-only — a live tag answers 409, never a silent delete. A tag a product still
     * carries is refused by the database (the link rows are its children, so nothing references
     * it), but the flush keeps that refusal inside the request instead of letting it escape at
     * commit as a 500.
     */
    @Transactional
    public void permanentDelete(Long id) {
        trashRules.refuseUnlessTrashed(Table.PRODUCT_TAGS, id);
        ProductTag tag = tagRepository.findDeletedById(id)
                .orElseThrow(ProductTagNotFoundException::new);
        String slug = tag.getSlug();
        tagRepository.delete(tag);
        tagRepository.flush();
        domainAudit.permanentlyDeleted(ENTITY, id, slug, tag.getDeletedAt());
        log.info("Product tag permanently deleted: {}", slug);
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
            List<String> missing = wanted.stream()
                    .filter(slug -> !foundSlugs.contains(slug))
                    .toList();
            log.warn("Unknown product tags requested: {}", missing);
            throw new ProductTagNotFoundException();
        }
        return new HashSet<>(found);
    }

    /**
     * The change-visible state of one tag, for an audit row: the slug a product
     * references and the English label an admin would recognise.
     */
    private static Map<String, Object> tagFields(ProductTag tag) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("slug", tag.getSlug());
        String name = tag.getTranslations() == null ? null : tag.getTranslations().stream()
                .filter(t -> "en".equals(t.getLanguage()))
                .map(ProductTagTranslation::getName)
                .findFirst().orElse(null);
        fields.put("name", name);
        return fields;
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
