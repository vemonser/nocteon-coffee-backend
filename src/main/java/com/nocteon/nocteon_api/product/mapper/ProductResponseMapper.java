package com.nocteon.nocteon_api.product.mapper;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethodTranslation;
import com.nocteon.nocteon_api.category.entity.CategoryTranslation;
import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVarietyTranslation;
import com.nocteon.nocteon_api.common.service.HtmlSanitizer;
import com.nocteon.nocteon_api.common.util.DiscountCalculator;
import com.nocteon.nocteon_api.farm.entity.FarmTranslation;
import com.nocteon.nocteon_api.origin.entity.OriginTranslation;
import com.nocteon.nocteon_api.pairing.entity.PairingTranslation;
import com.nocteon.nocteon_api.product.dto.response.CoffeeDetailsResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductBrewingMethodResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductMediaResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductPairingResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductVariantResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductWithScoreResponse;
import com.nocteon.nocteon_api.product.dto.response.summary.LockupResponse;
import com.nocteon.nocteon_api.product.dto.response.summary.LockupResponseImage;
import com.nocteon.nocteon_api.product.dto.response.summary.ProductOptionValueSummary;
import com.nocteon.nocteon_api.product.dto.response.summary.RoastLevelSummary;
import com.nocteon.nocteon_api.product.entity.CoffeeDetails;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductBrewingMethod;
import com.nocteon.nocteon_api.product.entity.ProductMedia;
import com.nocteon.nocteon_api.product.entity.ProductOptionTranslation;
import com.nocteon.nocteon_api.product.entity.ProductOptionValueTranslation;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.enums.MediaType;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.repository.CoffeeDetailsRepository;
import com.nocteon.nocteon_api.product.repository.ProductBrewingMethodRepository;
import com.nocteon.nocteon_api.product.repository.ProductMediaRepository;
import com.nocteon.nocteon_api.product.repository.ProductTranslationRepository;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;
import com.nocteon.nocteon_api.review.dto.response.ReviewResponse;
import com.nocteon.nocteon_api.review.repository.ReviewRepository;
import com.nocteon.nocteon_api.review.service.ReviewService;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevel;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevelTranslation;
import com.nocteon.nocteon_api.tastingNote.entity.TastingNoteTranslation;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProductResponseMapper {

        private final ProductTranslationRepository productTranslationRepository;
        private final ProductVariantRepository productVariantRepository;
        private final ProductMediaRepository productMediaRepository;
        private final ProductBrewingMethodRepository productBrewingMethodRepository;
        private final CoffeeDetailsRepository coffeeDetailsRepository;
        private final ReviewRepository reviewRepository;
        private final ReviewService reviewService;
        private final LookupResponseMapper lookupResponseMapper;
        private final LookupResponseImageMapper lookupResponseImageMapper;
        private final HtmlSanitizer htmlSanitizer;
        private final ProductTagMapper productTagMapper;

        public record VariantAggregate(int variantCount, int totalStock, int lowStockVariantCount,
                        BigDecimal minPrice, BigDecimal maxPrice) {
        }

        public ProductCardResponse buildListResponse(Product product, String language) {
                return buildListResponse(product, language, null);
        }

        public ProductCardResponse buildListResponse(Product product, String language, VariantAggregate aggregate) {
                Set<ProductTranslation> translations = product.getTranslations();

                ProductTranslation translation = translations.stream()
                                .filter(t -> t.getLanguage().equals(language))
                                .findFirst()
                                .orElse(translations.isEmpty() ? null : translations.stream().findFirst().orElse(null));

                ProductMedia primaryMedia = product.getMedia().stream()
                                .filter(ProductMedia::isPrimary)
                                .findFirst()
                                .orElse(product.getMedia().isEmpty() ? null
                                                : product.getMedia().stream().findFirst().orElse(null));

                List<ProductVariant> activeVariants = product.getVariants().stream()
                                .filter(ProductVariant::isActive)
                                .toList();

                ProductVariant lowestPriceVariant = activeVariants.stream()
                                .min(Comparator.comparing(ProductVariant::getPrice))
                                .orElse(null);

                BigDecimal minPrice;
                BigDecimal maxPrice;
                Integer variantCount;
                Integer totalStock;
                Integer lowStockVariantCount;

                if (aggregate != null) {
                        minPrice = aggregate.minPrice();
                        maxPrice = aggregate.maxPrice();
                        variantCount = aggregate.variantCount();
                        totalStock = aggregate.totalStock();
                        lowStockVariantCount = aggregate.lowStockVariantCount();
                } else {
                        minPrice = lowestPriceVariant != null ? lowestPriceVariant.getPrice() : null;
                        maxPrice = activeVariants.stream()
                                        .map(ProductVariant::getPrice)
                                        .max(BigDecimal::compareTo)
                                        .orElse(null);
                        variantCount = activeVariants.size();
                        totalStock = activeVariants.stream().mapToInt(ProductVariant::getStockQuantity).sum();
                        lowStockVariantCount = (int) activeVariants.stream().filter(ProductVariant::isLowStock).count();
                }

                Integer discountPercentage = lowestPriceVariant != null
                                ? DiscountCalculator.calculate(
                                                lowestPriceVariant.getPrice(),
                                                lowestPriceVariant.getCompareAtPrice())
                                : null;

                return ProductCardResponse.builder()
                                .id(product.getId())
                                .slug(product.getSlug())
                                .categorySlug(product.getCategory().getSlug())
                                .productType(product.getProductType())
                                .featured(product.isFeatured())
                                .isActive(product.isActive())
                                .name(translation != null ? translation.getName() : null)
                                .shortDescription(translation != null ? translation.getShortDescription() : null)
                                .primaryImageUrl(primaryMedia != null ? primaryMedia.getUrl() : null)
                                .minPrice(minPrice)
                                .maxPrice(maxPrice)
                                .discountPercentage(discountPercentage)
                                .variantCount(variantCount)
                                .variantsCount(variantCount)
                                .totalStock(totalStock)
                                .lowStockVariantCount(lowStockVariantCount)
                                .build();
        }

        public ProductResponse buildDetailResponse(Product product, String language) {

                List<ProductTranslation> translations = productTranslationRepository
                                .findByProductId(product.getId());

                ProductTranslation translation = translations.stream()
                                .filter(t -> t.getLanguage().equals(language))
                                .findFirst()
                                .orElse(translations.isEmpty() ? null : translations.get(0));

                List<ProductVariantResponse> variants = buildVariants(product, language);
                List<ProductMediaResponse> media = buildMedia(product);

                List<String> tastingNotes = product.getTastingNotes().stream()
                                .flatMap(tn -> tn.getTranslations().stream()
                                                .filter(t -> t.getLanguage().equals(language)))
                                .map(TastingNoteTranslation::getName)
                                .toList();

                List<ProductPairingResponse> pairings = product.getPairings().stream()
                                .map(p -> {
                                        String name = p.getTranslations().stream()
                                                        .filter(t -> t.getLanguage().equals(language))
                                                        .findFirst().map(PairingTranslation::getName)
                                                        .orElse(p.getSlug());

                                        String imageUrl = p.getImageUrl();

                                        return ProductPairingResponse.builder().pairingSlug(p.getSlug())
                                                        .pairingName(name).pairingImage(imageUrl).build();
                                }).toList();
                // .flatMap(p -> p.getTranslations().stream()
                // .filter(t -> t.getLanguage().equals(language)))
                // .map(PairingTranslation::getName)
                // .toList();

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

                CoffeeDetailsResponse coffeeDetailsResponse = null;

                if (product.getProductType() == ProductType.COFFEE) {
                        coffeeDetailsResponse = coffeeDetailsRepository
                                        .findByProductId(product.getId())
                                        .map(cd -> mapCoffeeDetails(cd, language))
                                        .orElse(null);
                }

                LockupResponseImage category = product.getCategory() != null
                                ? lookupResponseImageMapper.buildLookupImage(
                                                product.getCategory().getSlug(),
                                                product.getCategory().getTranslations(),
                                                language,
                                                product.getCategory().getImageUrl(),
                                                CategoryTranslation::getLanguage,
                                                CategoryTranslation::getName,
                                                CategoryTranslation::getDescription)
                                : null;

                LockupResponseImage origin = product.getOrigin() != null
                                ? lookupResponseImageMapper.buildLookupImage(
                                                product.getOrigin().getSlug(),
                                                product.getOrigin().getTranslations(),
                                                language,
                                                product.getOrigin().getImageUrl(),
                                                OriginTranslation::getLanguage,
                                                OriginTranslation::getName,
                                                OriginTranslation::getDescription)
                                : null;

                LockupResponseImage farm = product.getFarm() != null
                                ? lookupResponseImageMapper.buildLookupImage(
                                                product.getFarm().getSlug(),
                                                product.getFarm().getTranslations(),
                                                language,
                                                product.getFarm().getImageUrl(),        
                                                FarmTranslation::getLanguage,
                                                FarmTranslation::getName,
                                                FarmTranslation::getDescription)
                                : null;

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
                                .category(category)
                                .origin(origin)
                                .farm(farm)
                                .productType(product.getProductType())
                                .featured(product.isFeatured())
                                .isActive(product.isActive())
                                .name(translation != null ? translation.getName() : null)
                                .shortDescription(translation != null ? translation.getShortDescription() : null)
                                .description(translation != null ? translation.getDescription() : null)
                                .metaTitle(translation != null
                                                ? firstNonBlank(translation.getMetaTitle(), translation.getName())
                                                : null)
                                .metaDescription(translation != null
                                                ? firstNonBlank(
                                                                translation.getMetaDescription(),
                                                                translation.getShortDescription())
                                                : null)
                                .ogImageUrl(resolveOgImageUrl(product, media))
                                .coffeeDetails(coffeeDetailsResponse)
                                .variants(variants)
                                .media(media)
                                .tastingNotes(tastingNotes)
                                .pairings(pairings)
                                .brewingMethods(brewingMethods)
                                .averageRating(averageRating)
                                .reviewCount(reviewCount)
                                .recentReviews(recentReviews)
                                .tags(productTagMapper.toSummaries(product.getTags(), language))
                                .relatedProducts(product.getRelatedProducts().stream()
                                                .map(related -> buildListResponse(related, language))
                                                .toList())
                                .build();
        }

        public ProductWithScoreResponse buildProductWithScoreResponse(ProductBrewingMethod pbm, String language) {
                ProductCardResponse card = buildListResponse(pbm.getProduct(), language);
                return ProductWithScoreResponse.builder()
                                .product(card)
                                .score(pbm.getScore())
                                .build();
        }

        private CoffeeDetailsResponse mapCoffeeDetails(CoffeeDetails cd, String language) {
                String processingMethod = cd.getProcessingMethod() != null
                                ? cd.getProcessingMethod().getSlug()
                                : null;

                LockupResponse coffeeVariety = cd.getCoffeeVariety() != null
                                ? lookupResponseMapper.buildLookup(
                                                cd.getCoffeeVariety().getSlug(),
                                                cd.getCoffeeVariety().getTranslations(),
                                                language,
                                                CoffeeVarietyTranslation::getLanguage,
                                                CoffeeVarietyTranslation::getName,
                                                CoffeeVarietyTranslation::getDescription)
                                : null;

                RoastLevelSummary roastLevel = buildRoastLevel(cd, language);

                // The story is per language now; the coffee_details column is the
                // pre-migration fallback.
                String story = cd.getProduct() != null
                                ? cd.getProduct().getTranslations().stream()
                                                .filter(t -> t.getLanguage().equals(language))
                                                .map(ProductTranslation::getStory)
                                                .filter(java.util.Objects::nonNull)
                                                .findFirst()
                                                .orElse(cd.getStory())
                                : cd.getStory();

                return CoffeeDetailsResponse.builder()
                                .processingMethod(processingMethod)
                                .coffeeVariety(coffeeVariety)
                                .altitude(cd.getAltitude())
                                .harvestYear(cd.getHarvestYear())
                                .story(htmlSanitizer.sanitize(story))
                                .roastLevel(roastLevel)
                                .build();
        }

        List<ProductVariantResponse> buildVariants(Product product, String language) {
                return productVariantRepository
                                .findByProductId(product.getId()).stream()
                                .map(v -> ProductVariantResponse.builder()
                                                .id(v.getId())
                                                .sku(v.getSku())
                                                .price(v.getPrice())
                                                .compareAtPrice(v.getCompareAtPrice())
                                                .costPrice(v.getCostPrice())
                                                .imageUrl(v.getImageUrl())
                                                .stockQuantity(v.getStockQuantity())
                                                .reservedQuantity(v.getReservedQuantity())
                                                .reorderThreshold(v.getReorderThreshold())
                                                .isActive(v.isActive())
                                                .optionValues(buildOptionValueSummaries(v, language))
                                                .build())
                                .toList();
        }

        private List<ProductOptionValueSummary> buildOptionValueSummaries(ProductVariant variant, String language) {
                return variant.getOptionValues().stream()
                                .map(ov -> {
                                        String optionName = ov.getOption().getTranslations().stream()
                                                        .filter(t -> t.getLanguage().equals(language))
                                                        .findFirst()
                                                        .map(ProductOptionTranslation::getName)
                                                        .orElse(ov.getOption().getSlug());

                                        String value = ov.getTranslations().stream()
                                                        .filter(t -> t.getLanguage().equals(language))
                                                        .findFirst()
                                                        .map(ProductOptionValueTranslation::getValue)
                                                        .orElse(ov.getSlug());

                                        return ProductOptionValueSummary.builder()
                                                        .optionSlug(ov.getOption().getSlug())
                                                        .optionName(optionName)
                                                        .valueSlug(ov.getSlug())
                                                        .value(value)
                                                        .build();
                                })
                                .toList();
        }

        /**
         * The first value that is actually text. An empty SEO field is "not set", and the
         * public response fills it with the product name / short description so the storefront
         * never renders an empty {@code <title>}.
         */
        private static String firstNonBlank(String preferred, String fallback) {
                return preferred != null && !preferred.isBlank() ? preferred : fallback;
        }

        /**
         * The social card image.
         *
         * <p>An explicit {@code og_image_url} wins; otherwise the primary image does. A video is
         * never a social card, so a product whose primary media is a video falls back to its first
         * image rather than sending a video URL in {@code og:image}.
         */
        private String resolveOgImageUrl(Product product, List<ProductMediaResponse> media) {
                if (product.getOgImageUrl() != null && !product.getOgImageUrl().isBlank()) {
                        return product.getOgImageUrl();
                }
                return media.stream()
                                .filter(m -> m.getType() == MediaType.IMAGE)
                                .filter(ProductMediaResponse::isPrimary)
                                .findFirst()
                                .or(() -> media.stream().filter(m -> m.getType() == MediaType.IMAGE).findFirst())
                                .map(ProductMediaResponse::getUrl)
                                .orElse(null);
        }

        List<ProductMediaResponse> buildMedia(Product product) {
                return productMediaRepository
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
        }

        private RoastLevelSummary buildRoastLevel(CoffeeDetails coffeeDetails, String language) {

                if (coffeeDetails == null || coffeeDetails.getRoastLevel() == null) {
                        return null;
                }

                RoastLevel roastLevel = coffeeDetails.getRoastLevel();

                String name = roastLevel.getTranslations().stream()
                                .filter(t -> t.getLanguage().equals(language))
                                .findFirst()
                                .map(RoastLevelTranslation::getName)
                                .orElse(roastLevel.getSlug());

                return RoastLevelSummary.builder()
                                .slug(roastLevel.getSlug())
                                .name(name)
                                .color(roastLevel.getColor())
                                .build();

        }

}
