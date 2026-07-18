package com.nocteon.nocteon_api.product.mapper;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethodTranslation;
import com.nocteon.nocteon_api.category.entity.CategoryTranslation;
import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVarietyTranslation;
import com.nocteon.nocteon_api.common.util.DiscountCalculator;
import com.nocteon.nocteon_api.farm.entity.FarmTranslation;
import com.nocteon.nocteon_api.origin.entity.OriginTranslation;
import com.nocteon.nocteon_api.pairing.entity.PairingTranslation;
import com.nocteon.nocteon_api.product.dto.response.CoffeeDetailsResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductBrewingMethodResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductMediaResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductVariantResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductWithScoreResponse;
import com.nocteon.nocteon_api.product.dto.response.summary.LockupResponse;
import com.nocteon.nocteon_api.product.dto.response.summary.RoastLevelSummary;
import com.nocteon.nocteon_api.product.entity.CoffeeDetails;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductBrewingMethod;
import com.nocteon.nocteon_api.product.entity.ProductMedia;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
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

        public ProductCardResponse buildListResponse(Product product, String language) {
                List<ProductTranslation> translations = product.getTranslations();

                ProductTranslation translation = translations.stream()
                                .filter(t -> t.getLanguage().equals(language))
                                .findFirst()
                                .orElse(translations.isEmpty() ? null : translations.get(0));

                ProductMedia primaryMedia = product.getMedia().stream()
                                .filter(ProductMedia::isPrimary)
                                .findFirst()
                                .orElse(product.getMedia().isEmpty() ? null : product.getMedia().get(0));

                ProductVariant lowestPriceVariant = product.getVariants().stream()
                                .filter(ProductVariant::isActive)
                                .min(Comparator.comparing(ProductVariant::getPrice))
                                .orElse(null);

                BigDecimal minPrice = lowestPriceVariant != null
                                ? lowestPriceVariant.getPrice()
                                : null;

                BigDecimal maxPrice = product.getVariants().stream()
                                .filter(ProductVariant::isActive)
                                .map(ProductVariant::getPrice)
                                .max(BigDecimal::compareTo)
                                .orElse(null);

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
                                .build();
        }

        public ProductResponse buildDetailResponse(Product product, String language) {

                List<ProductTranslation> translations = productTranslationRepository
                                .findByProductId(product.getId());

                ProductTranslation translation = translations.stream()
                                .filter(t -> t.getLanguage().equals(language))
                                .findFirst()
                                .orElse(translations.isEmpty() ? null : translations.get(0));

                List<ProductVariantResponse> variants = buildVariants(product);
                List<ProductMediaResponse> media = buildMedia(product);

                List<String> tastingNotes = product.getTastingNotes().stream()
                                .flatMap(tn -> tn.getTranslations().stream()
                                                .filter(t -> t.getLanguage().equals(language)))
                                .map(TastingNoteTranslation::getName)
                                .toList();

                List<String> pairings = product.getPairings().stream()
                                .flatMap(p -> p.getTranslations().stream()
                                                .filter(t -> t.getLanguage().equals(language)))
                                .map(PairingTranslation::getName)
                                .toList();

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

                LockupResponse category = product.getCategory() != null
                                ? lookupResponseMapper.buildLookup(
                                                product.getCategory().getSlug(),
                                                product.getCategory().getTranslations(),
                                                language,
                                                CategoryTranslation::getLanguage,
                                                CategoryTranslation::getName,
                                                CategoryTranslation::getDescription)
                                : null;

                LockupResponse origin = product.getOrigin() != null
                                ? lookupResponseMapper.buildLookup(
                                                product.getOrigin().getSlug(),
                                                product.getOrigin().getTranslations(),
                                                language,
                                                OriginTranslation::getLanguage,
                                                OriginTranslation::getName,
                                                OriginTranslation::getDescription)
                                : null;

                LockupResponse farm = product.getFarm() != null
                                ? lookupResponseMapper.buildLookup(
                                                product.getFarm().getSlug(),
                                                product.getFarm().getTranslations(),
                                                language,
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

                return CoffeeDetailsResponse.builder()
                                .processingMethod(processingMethod)
                                .coffeeVariety(coffeeVariety)
                                .altitude(cd.getAltitude())
                                .harvestYear(cd.getHarvestYear())
                                .story(cd.getStory())
                                .roastLevel(roastLevel)
                                .build();
        }

        List<ProductVariantResponse> buildVariants(Product product) {
                return productVariantRepository
                                .findByProductId(product.getId()).stream()
                                .map(v -> ProductVariantResponse.builder()
                                                .id(v.getId())
                                                .sku(v.getSku())
                                                .price(v.getPrice())
                                                .compareAtPrice(v.getCompareAtPrice())
                                                .weightGrams(v.getWeightGrams())
                                                .grindType(v.getGrindType())
                                                .stockQuantity(v.getStockQuantity())
                                                .isActive(v.isActive())
                                                .build())
                                .toList();
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
