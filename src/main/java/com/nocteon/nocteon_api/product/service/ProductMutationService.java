package com.nocteon.nocteon_api.product.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.ai.service.ProductIndexingService;
import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.category.repository.CategoryRepository;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidProductTypeException;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.notFound.CategoryNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.FarmNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.OriginNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.ProductNotFoundException;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.common.transaction.AfterCommit;
import com.nocteon.nocteon_api.farm.entity.Farm;
import com.nocteon.nocteon_api.farm.repository.FarmRepository;
import com.nocteon.nocteon_api.origin.entity.Origin;
import com.nocteon.nocteon_api.origin.repository.OriginRepository;
import com.nocteon.nocteon_api.product.dto.request.ProductMediaRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductTranslationRequest;
import com.nocteon.nocteon_api.product.dto.response.DashboardProductResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductResponse;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductMedia;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.mapper.DashboardProductMapper;
import com.nocteon.nocteon_api.product.mapper.ProductResponseMapper;
import com.nocteon.nocteon_api.product.repository.ProductMediaRepository;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;
import com.nocteon.nocteon_api.product.repository.ProductTranslationRepository;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductMutationService {

    private final ProductRepository productRepository;
    private final ProductTranslationRepository productTranslationRepository;
    private final ProductMediaRepository productMediaRepository;
    private final CategoryRepository categoryRepository;
    private final OriginRepository originRepository;
    private final FarmRepository farmRepository;
    private final LookupServiceHelper helper;
    private final ProductMediaService productMediaService;
    private final ProductVariantRepository productVariantRepository;
    private final ProductResponseMapper productResponseMapper;
    private final DashboardProductMapper dashboardProductMapper;
    private final ProductVariantService productVariantService;
    private final ProductCoffeeDetailsService productCoffeeDetailsService;
    private final ProductRelationService productRelationService;
    private final ProductIndexingService productIndexingService;

    @Transactional
    public DashboardProductResponse create(ProductRequest request, List<MultipartFile> files) {
        helper.validateTranslations(request.getTranslations());
        productMediaService.validateMediaRequest(request.getMedia(), files);
        validateProductType(request);

        Category category = categoryRepository.findBySlug(request.getCategorySlug())
                .orElseThrow(CategoryNotFoundException::new);
        Origin origin = findOrigin(request.getOriginSlug());
        Farm farm = findFarm(request.getFarmSlug());

        String englishName = request.getTranslations().stream()
                .filter(t -> t.getLanguage().equals("en"))
                .findFirst()
                .map(ProductTranslationRequest::getName)
                .orElseThrow(InvalidTranslationException::new);

        String slug = helper.generateUniqueSlug(englishName, productRepository::existsBySlug);
        Product product = Product.builder()
                .slug(slug)
                .category(category)
                .origin(origin)
                .farm(farm)
                .productType(request.getProductType())
                .featured(request.isFeatured())
                .isActive(request.isActive())
                .build();
        product = productRepository.save(product);

        saveTranslations(product, request.getTranslations());
        productVariantService.saveVariants(product, request.getVariants());

        if (request.getProductType() == ProductType.COFFEE
                && request.getCoffeeDetails() != null) {
            productCoffeeDetailsService.saveCoffeeDetails(product, request.getCoffeeDetails());
        }

        productRelationService.attachTastingNotes(product, request.getTastingNoteSlugs());
        productRelationService.attachPairings(product, request.getPairingSlugs());
        productRelationService.saveBrewingMethods(product, request.getBrewingMethods());
        productMediaService.saveMedia(product, request.getMedia(), files);

        log.info("Product created with slug: {}", slug);
        // Registered last, so the hook fires once every relation is on the product: the indexer
        // re-reads it after the commit and would otherwise embed a product with no translations.
        scheduleIndexUpsert(product.getId(), slug);
        return dashboardProductMapper.buildDashboardResponse(
                productRepository.findBySlug(slug)
                        .orElseThrow(ProductNotFoundException::new));
    }

    @Transactional
    public DashboardProductResponse update(String slug, ProductRequest request, List<MultipartFile> files) {
        helper.validateTranslations(request.getTranslations());
        productMediaService.validateMediaRequest(request.getMedia(), files);
        validateProductType(request);

        Product product = productRepository.findBySlug(slug)
                .orElseThrow(ProductNotFoundException::new);

        product.setCategory(categoryRepository.findBySlug(request.getCategorySlug())
                .orElseThrow(CategoryNotFoundException::new));
        product.setOrigin(findOrigin(request.getOriginSlug()));
        product.setFarm(findFarm(request.getFarmSlug()));
        product.setProductType(request.getProductType());
        product.setFeatured(request.isFeatured());
        product.setActive(request.isActive());
        productRepository.save(product);

        upsertTranslations(product, request.getTranslations());
        productVariantService.replaceVariants(product, request.getVariants());
        productCoffeeDetailsService.replaceCoffeeDetails(product, request.getProductType(), request.getCoffeeDetails());
        productRelationService.replaceTastingNotes(product, request.getTastingNoteSlugs());
        productRelationService.replacePairings(product, request.getPairingSlugs());
        productRelationService.replaceBrewingMethods(product, request.getBrewingMethods());
        productMediaService.replaceMedia(product, request.getMedia(), files);

        productRepository.save(product);
        log.info("Product updated with slug: {}", slug);
        scheduleIndexUpsert(product.getId(), slug);
        return dashboardProductMapper.buildDashboardResponse(product);
    }

    @Transactional
    public DashboardProductResponse toggleActive(String slug, boolean active) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(ProductNotFoundException::new);
        product.setActive(active);
        productRepository.save(product);
        scheduleIndexUpsert(product.getId(), slug);
        return dashboardProductMapper.buildDashboardResponse(product);
    }

    @Transactional
    public DashboardProductResponse toggleFeatured(String slug, boolean featured) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(ProductNotFoundException::new);
        product.setFeatured(featured);
        productRepository.save(product);
        scheduleIndexUpsert(product.getId(), slug);
        return dashboardProductMapper.buildDashboardResponse(product);
    }

    @Transactional
    public DashboardProductResponse uploadMedia(
            String slug,
            List<ProductMediaRequest> mediaRequests,
            List<MultipartFile> files) {

        Product product = productRepository.findBySlug(slug)
                .orElseThrow(ProductNotFoundException::new);

        productMediaService.saveMedia(product, mediaRequests, files);

        return dashboardProductMapper.buildDashboardResponse(product);
    }

    @Transactional
    public void deleteMedia(Long mediaId) {
        ProductMedia media = productMediaRepository.findById(mediaId)
                .orElseThrow(ProductNotFoundException::new);
        productMediaService.delete(media);
    }

    @Transactional
    public void delete(String slug) {
        Product product = productRepository.findBySlugAndLanguage(
                slug, LocaleContextHolder.getLocale().getLanguage())
                .orElseThrow(ProductNotFoundException::new);
        product.softDelete();
        productRepository.save(product);
        log.info("Product soft deleted: {}", slug);
        scheduleIndexRemoval(slug);
    }

    /**
     * Permanently deletes a trashed product: its variants, then the product row itself —
     * translations, details and relations follow through the cascades — and only afterwards its
     * media assets on Cloudinary.
     *
     * <p>Trash-only: reads through {@code findDeletedById}, so a live or unknown id raises
     * {@link ProductNotFoundException} instead of deleting anything. Soft-delete first — this
     * never touches live rows.
     *
     * <p>Variants are removed explicitly rather than through the cascade: {@code ProductVariant}
     * carries {@code @SQLRestriction}, so already-soft-deleted variants are invisible to collection
     * loading and the cascade would miss them, failing on the foreign key instead.
     *
     * <p>Deletes the rows — and flushes, so a foreign-key conflict surfaces here — <em>before</em>
     * touching Cloudinary. If the database refuses the delete (a review, an order history row,
     * anything still referencing the product), the trashed product and every asset are untouched
     * and the admin can retry; the reverse order would strand a restored product without pictures.
     */
    @Transactional
    public void permanentDelete(Long id) {
        Product product = productRepository.findDeletedById(id)
                .orElseThrow(ProductNotFoundException::new);
        List<String> mediaUrls = new ArrayList<>(product.getMedia()).stream()
                .map(ProductMedia::getUrl)
                .toList();
        for (ProductVariant variant : productVariantRepository.findAllByProductIdIncludingDeleted(id)) {
            productVariantRepository.delete(variant);
        }
        productRepository.delete(product);
        productRepository.flush();
        String slug = product.getSlug();
        AfterCommit.run(() -> {
            for (String url : mediaUrls) {
                try {
                    helper.deleteMediaIfExists(url);
                } catch (Exception e) {
                    log.warn("Cloudinary delete failed for product {} media ({}): {}", slug, url, e.getMessage());
                }
            }
        });
        log.info("Product permanently deleted: {}", slug);
        scheduleIndexRemoval(slug);
    }

    /**
     * Re-embeds a product once the transaction that changed it has committed.
     *
     * <p>After commit, never inside the {@code @Transactional} body. Indexing before the commit
     * would embed a product that may still roll away, and a rollback would then leave a vector
     * pointing at something that does not exist — a phantom the search results could surface. After
     * the commit there is nothing left to roll back.
     *
     * <p>Only the id and the slug cross into the callback. The entity is detached by then and the
     * projection behind {@code upsertById} walks lazy relations, so it re-reads the product itself
     * rather than trusting this instance.
     *
     * <p>Every failure inside the hook is swallowed and logged. The product is already saved and the
     * response already built by the time this runs, so a downed embedding model must cost the admin
     * a stale search result — repaired by the nightly job — and not their product.
     */
    private void scheduleIndexUpsert(Long productId, String slug) {
        registerAfterCommit(() -> {
            try {
                productIndexingService.upsertById(productId);
            } catch (Exception e) {
                log.warn("Post-commit AI reindex failed for product {} ({}): {}",
                        productId, slug, e.getMessage());
            }
        });
    }

    /**
     * Re-embeds a restored product once the transaction that revived it has committed.
     *
     * <p>Soft {@code delete} schedules the document's removal and {@code permanentDelete} does too,
     * so without this a restored product would stay out of semantic search until the nightly
     * rebuild. Same after-commit, failure-tolerant semantics as {@link #scheduleIndexUpsert}.
     */
    public void scheduleIndexUpsertAfterRestore(Long productId, String slug) {
        scheduleIndexUpsert(productId, slug);
    }

    /**
     * Drops a soft-deleted product's vector document after the commit, for the same reasons and
     * with the same failure tolerance as {@link #scheduleIndexUpsert(Long, String)}.
     *
     * <p>A document left behind by a failed removal is not a wrong answer: every catalog query
     * filters on active products, so an orphaned document is unreachable and can never be served.
     * It costs storage until it is pruned, which nothing does automatically today.
     */
    private void scheduleIndexRemoval(String slug) {
        registerAfterCommit(() -> {
            try {
                productIndexingService.remove(slug);
            } catch (Exception e) {
                log.warn("Post-commit AI document removal failed for product {}: {}", slug, e.getMessage());
            }
        });
    }

    /**
     * Runs {@code action} once the current transaction commits.
     *
     * <p>The synchronization check is the guard, not a formality:
     * {@code registerSynchronization} throws {@code IllegalStateException} outside a transaction,
     * and a future caller that dropped {@code @Transactional} would otherwise have its working save
     * turned into a 500. Nothing to defer means nothing to do.
     */
    private void registerAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.debug("No active transaction, skipping the post-commit AI index hook");
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
        });
    }

    private void validateProductType(ProductRequest request) {
        if (request.getProductType() == ProductType.EQUIPMENT
                && request.getCoffeeDetails() != null) {
            throw new InvalidProductTypeException();
        }
    }

    private Origin findOrigin(String slug) {
        if (slug == null) {
            return null;
        }
        return originRepository.findBySlug(slug)
                .orElseThrow(OriginNotFoundException::new);
    }

    private Farm findFarm(String slug) {
        if (slug == null) {
            return null;
        }
        return farmRepository.findBySlug(slug)
                .orElseThrow(FarmNotFoundException::new);
    }

    private void saveTranslations(Product product, List<ProductTranslationRequest> requests) {
        List<ProductTranslation> translations = new ArrayList<>();
        for (ProductTranslationRequest t : requests) {
            translations.add(ProductTranslation.builder()
                    .product(product)
                    .language(t.getLanguage())
                    .name(t.getName())
                    .shortDescription(t.getShortDescription())
                    .description(t.getDescription())
                    .build());
        }
        productTranslationRepository.saveAll(translations);
    }

    private void upsertTranslations(Product product, List<ProductTranslationRequest> requests) {
        for (ProductTranslationRequest t : requests) {
            productTranslationRepository.findByProductIdAndLanguage(product.getId(), t.getLanguage())
                    .ifPresentOrElse(existing -> {
                        existing.setName(t.getName());
                        existing.setShortDescription(t.getShortDescription());
                        existing.setDescription(t.getDescription());
                        productTranslationRepository.save(existing);
                    }, () -> productTranslationRepository.save(ProductTranslation.builder()
                            .product(product)
                            .language(t.getLanguage())
                            .name(t.getName())
                            .shortDescription(t.getShortDescription())
                            .description(t.getDescription())
                            .build()));
        }
    }
}
