package com.nocteon.nocteon_api.product.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethod;
import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethodTranslation;
import com.nocteon.nocteon_api.brewingMethod.repository.BrewingMethodRepository;
import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.category.repository.CategoryRepository;
import com.nocteon.nocteon_api.cloudinary.service.CloudinaryService;
import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVariety;
import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVarietyTranslation;
import com.nocteon.nocteon_api.coffeeVariety.repository.CoffeeVarietyRepository;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidProductTypeException;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.notFound.BrewingMethodNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.CategoryNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.CoffeeVarietyNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.FarmNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.OriginNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.ProcessingMethodNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.ProductNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.RoastProfileNotFoundException;
import com.nocteon.nocteon_api.common.exception.product.DuplicateSkuException;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.farm.entity.Farm;
import com.nocteon.nocteon_api.farm.repository.FarmRepository;
import com.nocteon.nocteon_api.origin.entity.Origin;
import com.nocteon.nocteon_api.origin.repository.OriginRepository;
import com.nocteon.nocteon_api.pairing.entity.Pairing;
import com.nocteon.nocteon_api.pairing.entity.PairingTranslation;
import com.nocteon.nocteon_api.pairing.repository.PairingRepository;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethod;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethodTranslation;
import com.nocteon.nocteon_api.processingMethod.repository.ProcessingMethodRepository;
import com.nocteon.nocteon_api.product.dto.request.CoffeeDetailsRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductBrewingMethodRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductFilterRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductTranslationRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductVariantRequest;
import com.nocteon.nocteon_api.product.dto.response.CoffeeDetailsResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductBrewingMethodResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductMediaResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductVariantResponse;
import com.nocteon.nocteon_api.product.entity.CoffeeDetails;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductBrewingMethod;
import com.nocteon.nocteon_api.product.entity.ProductMedia;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.enums.MediaType;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.repository.CoffeeDetailsRepository;
import com.nocteon.nocteon_api.product.repository.ProductBrewingMethodRepository;
import com.nocteon.nocteon_api.product.repository.ProductMediaRepository;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.product.repository.ProductTranslationRepository;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;
import com.nocteon.nocteon_api.review.dto.response.ReviewResponse;
import com.nocteon.nocteon_api.review.repository.ReviewRepository;
import com.nocteon.nocteon_api.review.service.ReviewService;
import com.nocteon.nocteon_api.roastProfile.entity.RoastProfile;
import com.nocteon.nocteon_api.roastProfile.repository.RoastProfileRepository;
import com.nocteon.nocteon_api.tastingNote.entity.TastingNote;
import com.nocteon.nocteon_api.tastingNote.entity.TastingNoteTranslation;
import com.nocteon.nocteon_api.tastingNote.repository.TastingNoteRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;
    private final ProductTranslationRepository productTranslationRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductMediaRepository productMediaRepository;
    private final ProductBrewingMethodRepository productBrewingMethodRepository;
    private final CoffeeDetailsRepository coffeeDetailsRepository;
    private final CategoryRepository categoryRepository;
    private final OriginRepository originRepository;
    private final FarmRepository farmRepository;
    private final RoastProfileRepository roastProfileRepository;
    private final ProcessingMethodRepository processingMethodRepository;
    private final ReviewRepository reviewRepository;
    private final CoffeeVarietyRepository coffeeVarietyRepository;
    private final TastingNoteRepository tastingNoteRepository;
    private final PairingRepository pairingRepository;
    private final BrewingMethodRepository brewingMethodRepository;
    private final LookupServiceHelper helper;
    private final CloudinaryService cloudinaryService;
    private final ReviewService reviewService;

    public PageResponse<ProductResponse> getAll(ProductFilterRequest filter) {
        String language = LocaleContextHolder.getLocale().getLanguage();

        Page<Product> page = productRepository.findAllPublic(
                language,
                filter.getSearch(),
                filter.getCategorySlug(),
                filter.getOriginSlug(),
                filter.getProductType(),
                filter.getFeatured(),
                filter.getMinRating(),
                filter.toPageable());

        return PageResponse.of(page.map(p -> buildListResponse(p, language)));
    }

    public PageResponse<ProductResponse> getAllDashboard(ProductFilterRequest filter) {
        String language = LocaleContextHolder.getLocale().getLanguage();

        Page<Product> page = productRepository.findAllDashboard(
                filter.getSearch(),
                filter.getCategorySlug(),
                filter.getProductType(),
                filter.getIsActive(),
                filter.getFeatured(),
                filter.toPageable());

        return PageResponse.of(page.map(p -> buildListResponse(p, language)));
    }

    public ProductResponse getBySlug(String slug) {
        String language = LocaleContextHolder.getLocale().getLanguage();

        Product product = productRepository.findBySlugAndLanguage(slug, language)
                .orElseThrow(ProductNotFoundException::new);

        return buildDetailResponse(product, language);
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        helper.validateTranslations(request.getTranslations());

        // ===== 1. Validate Product Type =====
        if (request.getProductType() == ProductType.EQUIPMENT
                && request.getCoffeeDetails() != null) {
            throw new InvalidProductTypeException();
        }
        // ===== 2. جيب الـ Related Entities =====
        Category category = categoryRepository.findBySlug(request.getCategorySlug())
                .orElseThrow(CategoryNotFoundException::new);

        Origin origin = null;
        if (request.getOriginSlug() != null) {
            origin = originRepository.findBySlug(request.getOriginSlug())
                    .orElseThrow(OriginNotFoundException::new);
        }

        Farm farm = null;
        if (request.getFarmSlug() != null) {
            farm = farmRepository.findBySlug(request.getFarmSlug())
                    .orElseThrow(FarmNotFoundException::new);
        }

        RoastProfile roastProfile = null;
        if (request.getRoastProfileSlug() != null) {
            roastProfile = roastProfileRepository.findBySlug(request.getRoastProfileSlug())
                    .orElseThrow(RoastProfileNotFoundException::new);
        }
        // ===== 3. Generate Slug =====
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
                .roastProfile(roastProfile)
                .productType(request.getProductType())
                .featured(request.isFeatured())
                .isActive(request.isActive())
                .build();
        product = productRepository.save(product);

        // ===== 5. Save Translations =====
        final Product savedProduct = product;
        List<ProductTranslation> translations = new ArrayList<>();
        for (ProductTranslationRequest t : request.getTranslations()) {
            translations.add(ProductTranslation.builder()
                    .product(savedProduct)
                    .language(t.getLanguage())
                    .name(t.getName())
                    .shortDescription(t.getShortDescription())
                    .description(t.getDescription())
                    .build());
        }
        productTranslationRepository.saveAll(translations);

        // ===== 6. Save Variants =====
        for (ProductVariantRequest v : request.getVariants()) {
            if (productVariantRepository.existsBySku(v.getSku())) {
                throw new DuplicateSkuException();
            }
            productVariantRepository.save(ProductVariant.builder()
                    .product(savedProduct)
                    .sku(v.getSku())
                    .price(v.getPrice())
                    .weight(v.getWeight())
                    .grindType(v.getGrindType())
                    .stock(v.getStock())
                    .discount(v.getDiscount())
                    .isActive(v.getIsActive() != null ? v.getIsActive() : true)
                    .build());
        }
        // ===== 7. Save CoffeeDetails (بس للـ COFFEE) =====
        if (request.getProductType() == ProductType.COFFEE
                && request.getCoffeeDetails() != null) {

            CoffeeDetailsRequest cd = request.getCoffeeDetails();

            ProcessingMethod processingMethod = null;
            if (cd.getProcessingMethodSlug() != null) {
                processingMethod = processingMethodRepository
                        .findBySlug(cd.getProcessingMethodSlug())
                        .orElseThrow(ProcessingMethodNotFoundException::new);
            }

            CoffeeVariety coffeeVariety = null;
            if (cd.getCoffeeVarietySlug() != null) {
                coffeeVariety = coffeeVarietyRepository
                        .findBySlug(cd.getCoffeeVarietySlug())
                        .orElseThrow(CoffeeVarietyNotFoundException::new);
            }

            coffeeDetailsRepository.save(CoffeeDetails.builder()
                    .product(savedProduct)
                    .processingMethod(processingMethod)
                    .coffeeVariety(coffeeVariety)
                    .altitude(cd.getAltitude())
                    .harvestYear(cd.getHarvestYear())
                    .story(cd.getStory())
                    .build());
        }
        // ===== 8. Save TastingNotes =====
        if (request.getTastingNoteSlugs() != null
                && !request.getTastingNoteSlugs().isEmpty()) {
            List<TastingNote> tastingNotes = tastingNoteRepository
                    .findBySlugIn(request.getTastingNoteSlugs());
            savedProduct.getTastingNotes().addAll(tastingNotes);
            productRepository.save(savedProduct);
        }

        // ===== 9. Save Pairings =====
        if (request.getPairingSlugs() != null
                && !request.getPairingSlugs().isEmpty()) {
            List<Pairing> pairings = pairingRepository
                    .findBySlugIn(request.getPairingSlugs());
            savedProduct.getPairings().addAll(pairings);
            productRepository.save(savedProduct);
        }
        // ===== 10. Save BrewingMethods =====
        if (request.getBrewingMethods() != null
                && !request.getBrewingMethods().isEmpty()) {
            for (ProductBrewingMethodRequest bm : request.getBrewingMethods()) {
                BrewingMethod brewingMethod = brewingMethodRepository
                        .findBySlug(bm.getBrewingMethodSlug())
                        .orElseThrow(BrewingMethodNotFoundException::new);

                productBrewingMethodRepository.save(
                        ProductBrewingMethod.builder()
                                .product(savedProduct)
                                .brewingMethod(brewingMethod)
                                .score(bm.getScore())
                                .build());
            }
        }

        log.info("Product created with slug: {}", slug);
        return getBySlug(slug);
    }

    @Transactional
    public ProductResponse uploadMedia(String slug, MultipartFile file, MediaType type, boolean isPrimary) {
        Product product = productRepository.findBySlugAndLanguage(
                slug, LocaleContextHolder.getLocale().getLanguage())
                .orElseThrow(ProductNotFoundException::new);

        // لو isPrimary — شيل الـ primary القديمة
        if (isPrimary) {
            productMediaRepository.findByProductIdAndIsPrimary(product.getId(), true)
                    .ifPresent(media -> {
                        media.setPrimary(false);
                        productMediaRepository.save(media);
                    });
        }

        String folder = type == MediaType.IMAGE ? "products/images" : "products/videos";
        String url = cloudinaryService.uploadImage(file, folder);

        int sortOrder = productMediaRepository
                .findByProductIdOrderBySortOrder(product.getId()).size();

        productMediaRepository.save(ProductMedia.builder()
                .product(product)
                .url(url)
                .type(type)
                .isPrimary(isPrimary)
                .sortOrder(sortOrder)
                .build());

        return getBySlug(slug);
    }

    @Transactional
    public void deleteMedia(Long mediaId) {
        ProductMedia media = productMediaRepository.findById(mediaId)
                .orElseThrow(() -> new ProductNotFoundException());
        cloudinaryService.deleteImage(media.getUrl());
        productMediaRepository.delete(media);
    }

    @Transactional
    public void delete(String slug) {
        Product product = productRepository.findBySlugAndLanguage(
                slug, LocaleContextHolder.getLocale().getLanguage())
                .orElseThrow(ProductNotFoundException::new);
        product.softDelete();
        productRepository.save(product);
        log.info("Product soft deleted: {}", slug);
    }

    private ProductResponse buildListResponse(Product product, String language) {
        List<ProductTranslation> translations = productTranslationRepository
                .findByProductId(product.getId());

        ProductTranslation translation = translations.stream()
                .filter(t -> t.getLanguage().equals(language))
                .findFirst()
                .orElse(translations.isEmpty() ? null : translations.get(0));

        // Primary image بس
        ProductMedia primaryMedia = product.getMedia().stream()
                .filter(ProductMedia::isPrimary)
                .findFirst()
                .orElse(product.getMedia().isEmpty() ? null : product.getMedia().get(0));

        // Lowest price من الـ variants
        BigDecimal lowestPrice = product.getVariants().stream()
                .filter(ProductVariant::isActive)
                .map(ProductVariant::getPrice)
                .min(BigDecimal::compareTo)
                .orElse(null);

        return ProductResponse.builder()
                .id(product.getId())
                .slug(product.getSlug())
                .categorySlug(product.getCategory().getSlug())
                .productType(product.getProductType())
                .featured(product.isFeatured())
                .isActive(product.isActive())
                .name(translation != null ? translation.getName() : null)
                .shortDescription(translation != null ? translation.getShortDescription() : null)
                .primaryImageUrl(primaryMedia != null ? primaryMedia.getUrl() : null)
                .lowestPrice(lowestPrice)
                .build();
    }

    private ProductResponse buildDetailResponse(Product product, String language) {
        List<ProductTranslation> translations = productTranslationRepository
                .findByProductId(product.getId());

        ProductTranslation translation = translations.stream()
                .filter(t -> t.getLanguage().equals(language))
                .findFirst()
                .orElse(translations.isEmpty() ? null : translations.get(0));

        // Variants
        List<ProductVariantResponse> variants = productVariantRepository
                .findByProductId(product.getId()).stream()
                .map(v -> ProductVariantResponse.builder()
                        .id(v.getId())
                        .sku(v.getSku())
                        .price(v.getPrice())
                        .weight(v.getWeight())
                        .grindType(v.getGrindType())
                        .stock(v.getStock())
                        .discount(v.getDiscount())
                        .isActive(v.isActive())
                        .build())
                .toList();

        // Media
        List<ProductMediaResponse> media = productMediaRepository
                .findByProductIdOrderBySortOrder(product.getId()).stream()
                .map(m -> ProductMediaResponse.builder()
                        .id(m.getId())
                        .url(m.getUrl())
                        .altText(m.getAltText())
                        .type(m.getType())
                        .sortOrder(m.getSortOrder())
                        .isPrimary(m.isPrimary())
                        .build())
                .toList();

        // TastingNotes
        List<String> tastingNotes = product.getTastingNotes().stream()
                .flatMap(tn -> tn.getTranslations().stream()
                        .filter(t -> t.getLanguage().equals(language)))
                .map(TastingNoteTranslation::getName)
                .toList();

        // Pairings
        List<String> pairings = product.getPairings().stream()
                .flatMap(p -> p.getTranslations().stream()
                        .filter(t -> t.getLanguage().equals(language)))
                .map(PairingTranslation::getName)
                .toList();

        // BrewingMethods
        List<ProductBrewingMethodResponse> brewingMethods = productBrewingMethodRepository
                .findByProductId(product.getId()).stream()
                .map(bm -> {
                    String name = bm.getBrewingMethod().getTranslations().stream()
                            .filter(t -> t.getLanguage().equals(language))
                            .findFirst()
                            .map(BrewingMethodTranslation::getName)
                            .orElse(bm.getBrewingMethod().getSlug());

                    return ProductBrewingMethodResponse.builder()
                            .brewingMethodSlug(bm.getBrewingMethod().getSlug())
                            .brewingMethodName(name)
                            .score(bm.getScore())
                            .build();
                })
                .toList();

        // CoffeeDetails
        CoffeeDetailsResponse coffeeDetailsResponse = null;
        if (product.getProductType() == ProductType.COFFEE) {
            coffeeDetailsResponse = coffeeDetailsRepository
                    .findByProductId(product.getId())
                    .map(cd -> {
                        String processingMethod = cd.getProcessingMethod() != null
                                ? cd.getProcessingMethod().getTranslations().stream()
                                        .filter(t -> t.getLanguage().equals(language))
                                        .findFirst()
                                        .map(ProcessingMethodTranslation::getName)
                                        .orElse(null)
                                : null;

                        String coffeeVariety = cd.getCoffeeVariety() != null
                                ? cd.getCoffeeVariety().getTranslations().stream()
                                        .filter(t -> t.getLanguage().equals(language))
                                        .findFirst()
                                        .map(CoffeeVarietyTranslation::getName)
                                        .orElse(null)
                                : null;

                        return CoffeeDetailsResponse.builder()
                                .processingMethod(processingMethod)
                                .coffeeVariety(coffeeVariety)
                                .altitude(cd.getAltitude())
                                .harvestYear(cd.getHarvestYear())
                                .story(cd.getStory())
                                .build();
                    })
                    .orElse(null);
        }

        Double averageRating = reviewRepository
        .findAverageRatingByProductSlug(product.getSlug());

        Long reviewCount = reviewRepository
                .countByProductSlug(product.getSlug());

        List<ReviewResponse> recentReviews = reviewRepository
                .findByProductSlug(product.getSlug(), PageRequest.of(0, 3))
                .getContent()
                .stream()
                .map(reviewService::buildResponse)  
                .toList();

        return ProductResponse.builder()
                .id(product.getId())
                .slug(product.getSlug())
                .categorySlug(product.getCategory().getSlug())
                .originSlug(product.getOrigin() != null ? product.getOrigin().getSlug() : null)
                .farmSlug(product.getFarm() != null ? product.getFarm().getSlug() : null)
                .roastProfileSlug(product.getRoastProfile() != null
                        ? product.getRoastProfile().getSlug()
                        : null)
                .productType(product.getProductType())
                .featured(product.isFeatured())
                .isActive(product.isActive())
                .name(translation != null ? translation.getName() : null)
                .shortDescription(translation != null ? translation.getShortDescription() : null)
                .description(translation != null ? translation.getDescription() : null)
                .coffeeDetails(coffeeDetailsResponse)
                .variants(variants)
                .media(media)
                .tastingNotes(tastingNotes)
                .pairings(pairings)
                .brewingMethods(brewingMethods)
                .averageRating(averageRating)
                .reviewCount(reviewCount)
                .recentReviews(recentReviews)
                .build();
    }

}
