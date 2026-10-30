package com.nocteon.nocteon_api.product.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.ai.service.ProductIndexingService;
import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.audit.service.AuditLogService;
import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.category.repository.CategoryRepository;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidProductTypeException;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.notFound.CategoryNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.FarmNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.OriginNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.ProductNotFoundException;
import com.nocteon.nocteon_api.common.service.HtmlSanitizer;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.common.service.TrashRules;
import com.nocteon.nocteon_api.common.service.TrashRules.Reference;
import com.nocteon.nocteon_api.common.service.TrashRules.Table;
import com.nocteon.nocteon_api.common.security.ActorResolver;
import com.nocteon.nocteon_api.common.security.AuditActor;
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
    private final HtmlSanitizer htmlSanitizer;
    private final ProductTagService productTagService;
    private final AuditLogService auditLogService;
    private final TrashRules trashRules;

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
                .ogImageUrl(normalizeOgImageUrl(request.getOgImageUrl()))
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
        productTagService.replaceTags(product, request.getTagSlugs());
        productRelationService.replaceRelatedProducts(product, request.getRelatedProductSlugs());
        productRelationService.saveBrewingMethods(product, request.getBrewingMethods());
        productMediaService.saveMedia(product, request.getMedia(), files);

        log.info("Product created with slug: {}", slug);
        // Registered last, so the hook fires once every relation is on the product: the indexer
        // re-reads it after the commit and would otherwise embed a product with no translations.
        scheduleIndexUpsert(product.getId(), slug);
        Product saved = productRepository.findBySlug(slug)
                .orElseThrow(ProductNotFoundException::new);
        // The audit row is written in this transaction: a create that fails afterwards
        // (the index upsert, a constraint) leaves no row claiming the product exists.
        auditCreate(saved, englishName, null);
        return dashboardProductMapper.buildDashboardResponse(saved);
    }

    @Transactional
    public DashboardProductResponse update(String slug, ProductRequest request, List<MultipartFile> files) {
        helper.validateTranslations(request.getTranslations());
        productMediaService.validateMediaRequest(request.getMedia(), files);
        validateProductType(request);

        Product product = productRepository.findBySlug(slug)
                .orElseThrow(ProductNotFoundException::new);

        // The BEFORE, read before anything is overwritten. The audit row then carries only the
        // fields this save actually changed — an edit that touched the description must not
        // report a new price.
        Map<String, Object> before = productFields(product);
        Map<String, Map<String, Object>> variantsBefore = variantFields(product);

        product.setCategory(categoryRepository.findBySlug(request.getCategorySlug())
                .orElseThrow(CategoryNotFoundException::new));
        product.setOrigin(findOrigin(request.getOriginSlug()));
        product.setFarm(findFarm(request.getFarmSlug()));
        product.setProductType(request.getProductType());
        product.setFeatured(request.isFeatured());
        product.setActive(request.isActive());
        product.setOgImageUrl(normalizeOgImageUrl(request.getOgImageUrl()));
        productRepository.save(product);

        upsertTranslations(product, request.getTranslations());
        productVariantService.replaceVariants(product, request.getVariants());
        productCoffeeDetailsService.replaceCoffeeDetails(product, request.getProductType(), request.getCoffeeDetails());
        productRelationService.replaceTastingNotes(product, request.getTastingNoteSlugs());
        productRelationService.replacePairings(product, request.getPairingSlugs());
        productTagService.replaceTags(product, request.getTagSlugs());
        productRelationService.replaceRelatedProducts(product, request.getRelatedProductSlugs());
        productRelationService.replaceBrewingMethods(product, request.getBrewingMethods());
        productMediaService.replaceMedia(product, request.getMedia(), files);

        productRepository.save(product);
        log.info("Product updated with slug: {}", slug);
        auditUpdate(product, before, variantsBefore);
        scheduleIndexUpsert(product.getId(), slug);
        return dashboardProductMapper.buildDashboardResponse(product);
    }

    /**
     * FE-12: copy a product into an editable draft.
     *
     * <p>Everything the admin would otherwise retype is carried over — translations, coffee
     * details, media, tasting notes, pairings, brewing methods, tags and related products — while
     * three things are deliberately different:
     *
     * <ul>
     *   <li>the slug is {@code <slug>-copy} (then {@code -copy-2}, ...), checked against trashed
     *       rows too, because the unique index covers them;
     *   <li>every variant gets a new SKU ({@code <sku>-copy}) — two live rows cannot share one;
     *   <li>stock starts at <b>0</b>. Duplicating a row does not duplicate the coffee on the
     *       shelf, and copying the count would silently double the inventory report.
     * </ul>
     *
     * <p>The copy is inactive so it cannot reach the storefront before the admin edits it.
     */
    @Transactional
    public DashboardProductResponse duplicate(String slug) {
        Product source = productRepository.findBySlug(slug)
                .orElseThrow(ProductNotFoundException::new);

        String copySlug = uniqueCopySlug(source.getSlug());
        Product copy = Product.builder()
                .slug(copySlug)
                .category(source.getCategory())
                .origin(source.getOrigin())
                .farm(source.getFarm())
                .productType(source.getProductType())
                .featured(false)
                .isActive(false)
                .ogImageUrl(source.getOgImageUrl())
                .build();
        copy = productRepository.save(copy);

        copyTranslations(source, copy);
        copyVariants(source, copy);
        copyMedia(source, copy);
        productCoffeeDetailsService.copyCoffeeDetails(source, copy);
        productRelationService.replaceTastingNotes(copy, tastingNoteSlugs(source));
        productRelationService.replacePairings(copy, pairingSlugs(source));
        productRelationService.replaceBrewingMethods(copy, brewingMethodRequests(source));
        productTagService.replaceTags(copy, source.getTags().stream().map(t -> t.getSlug()).toList());
        productRelationService.replaceRelatedProducts(copy,
                productRelationService.relatedProductsOf(source.getId()).stream()
                        .map(Product::getSlug)
                        .toList());

        productRepository.save(copy);
        log.info("Product duplicated: {} -> {}", slug, copySlug);
        auditCreate(copy, englishNameOf(source), source.getSlug());
        scheduleIndexUpsert(copy.getId(), copySlug);
        return dashboardProductMapper.buildDashboardResponse(copy);
    }

    private String uniqueCopySlug(String sourceSlug) {
        String base = sourceSlug + "-copy";
        // Two admins duplicating the same product at the same moment would both pass the
        // existence check and one would fail on the unique index; the advisory lock is the same
        // mechanism the tag and lookup services use.
        helper.acquireNameLock("product", "slug", base);
        String candidate = base;
        int counter = 2;
        while (productRepository.existsBySlugIncludingDeleted(candidate)) {
            candidate = base + "-" + counter++;
        }
        return candidate;
    }

    /**
     * {@code sku} is {@code varchar(150)}: a source SKU near the limit would overflow once
     * {@code -copy} (and possibly {@code -2}) is appended, failing on the column instead of the
     * index. The base is trimmed so the longest suffix still fits.
     */
    private static String uniqueCopySku(String sourceSku, java.util.function.Predicate<String> taken) {
        int room = 150 - "-copy-99".length();
        String trimmed = sourceSku.length() > room ? sourceSku.substring(0, room) : sourceSku;
        String base = trimmed + "-copy";
        String candidate = base;
        int counter = 2;
        while (taken.test(candidate)) {
            candidate = base + "-" + counter++;
        }
        return candidate;
    }

    private void copyTranslations(Product source, Product target) {
        List<ProductTranslation> copies = new ArrayList<>();
        for (ProductTranslation t : source.getTranslations()) {
            copies.add(ProductTranslation.builder()
                    .product(target)
                    .language(t.getLanguage())
                    .name(t.getName())
                    .shortDescription(t.getShortDescription())
                    .description(t.getDescription())
                    .story(t.getStory())
                    .metaTitle(t.getMetaTitle())
                    .metaDescription(t.getMetaDescription())
                    .build());
        }
        productTranslationRepository.saveAll(copies);
    }

    private void copyVariants(Product source, Product target) {
        for (ProductVariant v : source.getVariants()) {
            productVariantRepository.save(ProductVariant.builder()
                    .product(target)
                    .sku(uniqueCopySku(v.getSku(), productVariantRepository::existsBySkuIncludingDeleted))
                    .price(v.getPrice())
                    .compareAtPrice(v.getCompareAtPrice())
                    .costPrice(v.getCostPrice())
                    .imageUrl(v.getImageUrl())
                    // FE-15: the barcode is deliberately NOT copied. It identifies one physical
                    // box, and the partial unique index would reject the second row that claims
                    // it — the copy has to be labelled by the admin who prints its label.
                    .stockQuantity(0)
                    .reservedQuantity(0)
                    .reorderThreshold(v.getReorderThreshold())
                    .isActive(v.isActive())
                    .version(0L)
                    // A variant built without option values carries a null set (Lombok's builder
                    // bypasses the field initializer), so the copy has to allow for it.
                    .optionValues(v.getOptionValues() == null
                            ? new HashSet<>()
                            : new HashSet<>(v.getOptionValues()))
                    .build());
        }
    }

    private void copyMedia(Product source, Product target) {
        for (ProductMedia m : source.getMedia()) {
            productMediaRepository.save(ProductMedia.builder()
                    .product(target)
                    .url(m.getUrl())
                    .altText(m.getAltText())
                    .type(m.getType())
                    .sortOrder(m.getSortOrder())
                    .isPrimary(m.isPrimary())
                    .build());
        }
    }

    private static List<String> tastingNoteSlugs(Product product) {
        return product.getTastingNotes().stream().map(tn -> tn.getSlug()).toList();
    }

    private static List<String> pairingSlugs(Product product) {
        return product.getPairings().stream().map(p -> p.getSlug()).toList();
    }

    private static List<com.nocteon.nocteon_api.product.dto.request.ProductBrewingMethodRequest> brewingMethodRequests(
            Product product) {
        return product.getBrewingMethods().stream()
                .map(bm -> {
                    com.nocteon.nocteon_api.product.dto.request.ProductBrewingMethodRequest request =
                            new com.nocteon.nocteon_api.product.dto.request.ProductBrewingMethodRequest();
                    request.setBrewingMethodSlug(bm.getBrewingMethod().getSlug());
                    request.setScore(bm.getScore());
                    return request;
                })
                .toList();
    }

    @Transactional
    public DashboardProductResponse toggleActive(String slug, boolean active) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(ProductNotFoundException::new);
        boolean before = product.isActive();
        product.setActive(active);
        productRepository.save(product);
        if (before != active) {
            Map<String, Object> oldValues = new LinkedHashMap<>();
            oldValues.put("isActive", before);
            Map<String, Object> newValues = new LinkedHashMap<>();
            newValues.put("isActive", active);
            audit(AuditAction.PRODUCT_TOGGLE_ACTIVE, product, oldValues, newValues, null);
        }
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
        // An order line is the sales history: trashing the product would hide it from the
        // catalogue while the order keeps pointing at its variants, and only a restore could
        // put it back. A cart is the same problem one step earlier — the row would survive the
        // trash and then block the purge forever.
        trashRules.refuseIfReferenced(Reference.PRODUCT_ORDER_LINES, product.getId());
        trashRules.refuseIfReferenced(Reference.PRODUCT_CART_ITEMS, product.getId());
        product.softDelete();
        productRepository.save(product);
        log.info("Product soft deleted: {}", slug);

        // "Who trashed this product" — the row keeps the slug so the question is
        // answerable after the product is filtered out of every listing.
        Map<String, Object> oldValues = new LinkedHashMap<>();
        oldValues.put("deletedAt", null);
        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("deletedAt", product.getDeletedAt().toString());
        audit(AuditAction.PRODUCT_DELETE, product, oldValues, newValues, null);

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
        trashRules.refuseUnlessTrashed(Table.PRODUCTS, id);
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
        Map<String, Object> deleted = new LinkedHashMap<>();
        deleted.put("slug", slug);
        deleted.put("deletedAt", product.getDeletedAt() == null ? null : product.getDeletedAt().toString());
        audit(AuditAction.ENTITY_PERMANENT_DELETE, product, deleted, null, null);
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

    // ─── the audit rows ─────────────────────────────────────────────────────

    /**
     * The fields a save can change, as a flat map.
     *
     * <p>Deliberately small: the values the detail drawer can render and an
     * operator can read — the name, the lookups by slug, the two flags and the
     * social image. Not the description (rich text, kilobytes), not the media,
     * not the relations: those are not what "who changed this product" asks.
     */
    private static Map<String, Object> productFields(Product product) {
        return productFields(product, englishNameOf(product));
    }

    /** The English name, from the loaded translations (null when the product has none yet). */
    private static String englishNameOf(Product product) {
        if (product.getTranslations() == null) {
            return null;
        }
        return product.getTranslations().stream()
                .filter(t -> "en".equals(t.getLanguage()))
                .map(ProductTranslation::getName)
                .findFirst()
                .orElse(null);
    }

    /**
     * The same fields, with the name the CALLER knows.
     *
     * <p>A product that was just created has its translations saved through their own
     * repository, so its in-memory collection is still the empty set it was built with —
     * reading the name from there would record null in the one row that should say what
     * was created. The create and duplicate paths pass the name they already have.
     */
    private static Map<String, Object> productFields(Product product, String name) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("name", name);
        fields.put("categorySlug", product.getCategory() == null ? null : product.getCategory().getSlug());
        fields.put("originSlug", product.getOrigin() == null ? null : product.getOrigin().getSlug());
        fields.put("farmSlug", product.getFarm() == null ? null : product.getFarm().getSlug());
        fields.put("productType", product.getProductType() == null ? null : product.getProductType().name());
        fields.put("featured", product.isFeatured());
        fields.put("isActive", product.isActive());
        fields.put("ogImageUrl", product.getOgImageUrl());
        return fields;
    }

    /**
     * The price and the barcode of each live variant, keyed by SKU.
     *
     * <p>These two are the variant facts a product edit is worth remembering for
     * (a price is money, a barcode identifies a box). The stock number is NOT
     * duplicated here: it has its own row in {@code inventory_movements}, and two
     * records of one number would eventually disagree.
     *
     * <p>Prices are normalised through {@code stripTrailingZeros} so {@code 650}
     * and {@code 650.00} are the same price and not a "change".
     */
    private static Map<String, Map<String, Object>> variantFields(Product product) {
        Map<String, Map<String, Object>> variants = new LinkedHashMap<>();
        if (product.getVariants() == null) {
            return variants;
        }
        for (ProductVariant variant : product.getVariants()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("price", variant.getPrice() == null
                    ? null
                    : variant.getPrice().stripTrailingZeros().toPlainString());
            fields.put("barcode", variant.getBarcode());
            variants.put(variant.getSku(), fields);
        }
        return variants;
    }

    /** The old/new pair for the keys whose value differs; nothing for the keys that did not move. */
    private static Diff diff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> oldValues = new LinkedHashMap<>();
        Map<String, Object> newValues = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : after.entrySet()) {
            if (!Objects.equals(before.get(entry.getKey()), entry.getValue())) {
                oldValues.put(entry.getKey(), before.get(entry.getKey()));
                newValues.put(entry.getKey(), entry.getValue());
            }
        }
        return new Diff(oldValues, newValues);
    }

    /** The same, one level down: per SKU, the variant fields that changed. */
    private static Diff diffVariants(Map<String, Map<String, Object>> before,
            Map<String, Map<String, Object>> after) {
        Map<String, Object> oldValues = new LinkedHashMap<>();
        Map<String, Object> newValues = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : after.entrySet()) {
            Map<String, Object> was = before.get(entry.getKey());
            if (was == null) {
                oldValues.put(entry.getKey(), null);
                newValues.put(entry.getKey(), entry.getValue());
                continue;
            }
            Diff fieldDiff = diff(was, entry.getValue());
            if (!fieldDiff.newValues().isEmpty()) {
                oldValues.put(entry.getKey(), fieldDiff.oldValues());
                newValues.put(entry.getKey(), fieldDiff.newValues());
            }
        }
        for (Map.Entry<String, Map<String, Object>> entry : before.entrySet()) {
            if (!after.containsKey(entry.getKey())) {
                oldValues.put(entry.getKey(), entry.getValue());
                newValues.put(entry.getKey(), null);
            }
        }
        return new Diff(oldValues, newValues);
    }

    /** The old/new halves of one change. Empty means "nothing differed". */
    private record Diff(Map<String, Object> oldValues, Map<String, Object> newValues) {
    }

    private void auditCreate(Product product, String name, String duplicatedFrom) {
        Map<String, Object> newValues = productFields(product, name);
        Map<String, Object> metadata = null;
        if (duplicatedFrom != null) {
            // A copy is a create, but the reader deserves to know where it came from.
            metadata = new LinkedHashMap<>();
            metadata.put("duplicatedFrom", duplicatedFrom);
        }
        audit(AuditAction.PRODUCT_CREATE, product, null, newValues, metadata);
    }

    private void auditUpdate(Product product, Map<String, Object> before,
            Map<String, Map<String, Object>> variantsBefore) {
        Diff fields = diff(before, productFields(product));
        Diff variants = diffVariants(variantsBefore, variantFields(product));
        boolean variantChanged = !variants.oldValues().isEmpty() || !variants.newValues().isEmpty();

        Map<String, Object> newValues = new LinkedHashMap<>(fields.newValues());
        Map<String, Object> oldValues = new LinkedHashMap<>(fields.oldValues());
        if (variantChanged) {
            // Under "variants", keyed by SKU, so the drawer shows the price or barcode
            // change next to the field changes rather than in a second row.
            oldValues.put("variants", variants.oldValues());
            newValues.put("variants", variants.newValues());
        }

        audit(AuditAction.PRODUCT_UPDATE, product,
                emptyToNull(oldValues), emptyToNull(newValues), null);
    }

    private static Map<String, Object> emptyToNull(Map<String, Object> values) {
        return values.isEmpty() ? null : values;
    }

    /**
     * One audit row for this product, with the acting admin as its actor.
     *
     * <p>Written in the caller's transaction (the create/update/delete it
     * describes), so a rolled-back save leaves no row. No principal — a seed, a
     * scheduled job, a test — records no actor rather than the string "system".
     */
    private void audit(AuditAction action, Product product, Map<String, Object> oldValues,
            Map<String, Object> newValues, Map<String, Object> metadata) {
        AuditActor actor = ActorResolver.currentAuditActor();
        auditLogService.logSuccess(action, actor.userId(), actor.userEmail(),
                "Product", product.getId(), product.getSlug(),
                null, null, oldValues, newValues, metadata);
    }

    /**
     * A blank {@code og_image_url} from the form means "no custom card", not an empty string:
     * stored as null so the response falls back to the primary image instead of a broken URL.
     */
    private static String normalizeOgImageUrl(String url) {
        return url == null || url.isBlank() ? null : url.trim();
    }

    /**
     * A blank meta field is "not set", not a string of spaces: stored as null so the public
     * response can fall back to the product name / short description.
     */
    /** The story is rich text (the journal editor); legacy plain text passes through as text. */
    private String sanitizeStory(String story) {
        return htmlSanitizer.sanitize(story);
    }

    private static String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
                    .story(sanitizeStory(t.getStory()))
                    .metaTitle(normalizeText(t.getMetaTitle()))
                    .metaDescription(normalizeText(t.getMetaDescription()))
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
                        existing.setStory(sanitizeStory(t.getStory()));
                        existing.setMetaTitle(normalizeText(t.getMetaTitle()));
                        existing.setMetaDescription(normalizeText(t.getMetaDescription()));
                        productTranslationRepository.save(existing);
                    }, () -> productTranslationRepository.save(ProductTranslation.builder()
                            .product(product)
                            .language(t.getLanguage())
                            .name(t.getName())
                            .shortDescription(t.getShortDescription())
                            .description(t.getDescription())
                            .story(sanitizeStory(t.getStory()))
                            .metaTitle(normalizeText(t.getMetaTitle()))
                            .metaDescription(normalizeText(t.getMetaDescription()))
                            .build()));
        }
    }
}
