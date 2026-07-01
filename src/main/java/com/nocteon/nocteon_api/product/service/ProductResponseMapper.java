package com.nocteon.nocteon_api.product.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethodTranslation;
import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVarietyTranslation;
import com.nocteon.nocteon_api.origin.entity.Origin;
import com.nocteon.nocteon_api.pairing.entity.Pairing;
import com.nocteon.nocteon_api.pairing.entity.PairingTranslation;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethodTranslation;
import com.nocteon.nocteon_api.product.dto.response.CoffeeDetailsResponse;
import com.nocteon.nocteon_api.product.dto.response.DashboardCoffeeDetailsResponse;
import com.nocteon.nocteon_api.product.dto.response.DashboardProductResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductBrewingMethodResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductMediaResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductTranslationResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductVariantResponse;
import com.nocteon.nocteon_api.product.dto.response.RoastLevelSummary;
import com.nocteon.nocteon_api.product.entity.CoffeeDetails;
import com.nocteon.nocteon_api.product.entity.Product;
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
import com.nocteon.nocteon_api.tastingNote.entity.TastingNote;
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

        public ProductResponse buildListResponse(Product product, String language) {
                List<ProductTranslation> translations = productTranslationRepository
                                .findByProductId(product.getId());

                ProductTranslation translation = translations.stream()
                                .filter(t -> t.getLanguage().equals(language))
                                .findFirst()
                                .orElse(translations.isEmpty() ? null : translations.get(0));

                ProductMedia primaryMedia = product.getMedia().stream()
                                .filter(ProductMedia::isPrimary)
                                .findFirst()
                                .orElse(product.getMedia().isEmpty() ? null : product.getMedia().get(0));

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

        public DashboardProductResponse buildDashboardResponse(Product product) {
                String language = LocaleContextHolder.getLocale().getLanguage();

                List<ProductTranslationResponse> translations = productTranslationRepository
                                .findByProductId(product.getId()).stream()
                                .map(t -> ProductTranslationResponse.builder()
                                                .language(t.getLanguage())
                                                .name(t.getName())
                                                .shortDescription(t.getShortDescription())
                                                .description(t.getDescription())
                                                .build())
                                .toList();

                ProductTranslationResponse translation = translations.stream()
                                .filter(t -> t.getLanguage().equals(language))
                                .findFirst()
                                .orElse(translations.isEmpty() ? null : translations.get(0));

                List<ProductVariantResponse> variants = buildVariants(product);
                List<ProductMediaResponse> media = buildMedia(product);

                ProductMediaResponse primaryMedia = media.stream()
                                .filter(ProductMediaResponse::isPrimary)
                                .findFirst()
                                .orElse(media.isEmpty() ? null : media.get(0));

                BigDecimal minPrice = variants.stream()
                                .filter(ProductVariantResponse::isActive)
                                .map(ProductVariantResponse::getPrice)
                                .min(BigDecimal::compareTo)
                                .orElse(null);

                BigDecimal maxPrice = variants.stream()
                                .filter(ProductVariantResponse::isActive)
                                .map(ProductVariantResponse::getPrice)
                                .max(BigDecimal::compareTo)
                                .orElse(null);

                List<String> tastingNotes = product.getTastingNotes().stream()
                                .map(TastingNote::getSlug)
                                .toList();

                List<String> pairings = product.getPairings().stream()
                                .map(Pairing::getSlug)
                                .toList();

                List<ProductBrewingMethodResponse> brewingMethods = productBrewingMethodRepository
                                .findByProductId(product.getId()).stream()
                                .map(bm -> ProductBrewingMethodResponse.builder()
                                                .brewingMethodSlug(bm.getBrewingMethod().getSlug())
                                                .brewingMethodName(null)
                                                .score(bm.getScore())
                                                .build())
                                .toList();
                DashboardCoffeeDetailsResponse coffeeDetailsResponse = coffeeDetailsRepository
                                .findByProductId(product.getId())
                                .map(cd -> {

                                        RoastLevelSummary roastLevel = buildRoastLevel(cd, language);

                                        return DashboardCoffeeDetailsResponse.builder()
                                                        .processingMethodSlug(
                                                                        cd.getProcessingMethod() != null
                                                                                        ? cd.getProcessingMethod()
                                                                                                        .getSlug()
                                                                                        : null)
                                                        .coffeeVarietySlug(
                                                                        cd.getCoffeeVariety() != null
                                                                                        ? cd.getCoffeeVariety()
                                                                                                        .getSlug()
                                                                                        : null)
                                                        .altitude(cd.getAltitude())
                                                        .harvestYear(cd.getHarvestYear())
                                                        .story(cd.getStory())
                                                        .roastLevel(roastLevel)
                                                        .build();
                                })
                                .orElse(null);

                return DashboardProductResponse.builder()
                                .id(product.getId())
                                .slug(product.getSlug())
                                .categorySlug(product.getCategory().getSlug())
                                .originSlug(slug(product.getOrigin()))
                                .farmSlug(product.getFarm() != null ? product.getFarm().getSlug() : null)
                                .productType(product.getProductType())
                                .featured(product.isFeatured())
                                .isActive(product.isActive())
                                .name(translation != null ? translation.getName() : null)
                                .shortDescription(translation != null ? translation.getShortDescription() : null)
                                .description(translation != null ? translation.getDescription() : null)
                                .imageUrl(primaryMedia != null ? primaryMedia.getUrl() : null)
                                .primaryImageUrl(primaryMedia != null ? primaryMedia.getUrl() : null)
                                .minPrice(minPrice)
                                .maxPrice(maxPrice)
                                .lowestPrice(minPrice)
                                .translations(translations)
                                .coffeeDetails(coffeeDetailsResponse)
                                .variants(variants)
                                .media(media)
                                .tastingNotes(tastingNotes)
                                .pairings(pairings)
                                .brewingMethods(brewingMethods)
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
                                        .map(cd -> {

                                                String processingMethod = cd.getProcessingMethod() != null
                                                                ? cd.getProcessingMethod().getTranslations().stream()
                                                                                .filter(t -> t.getLanguage()
                                                                                                .equals(language))
                                                                                .findFirst()
                                                                                .map(ProcessingMethodTranslation::getName)
                                                                                .orElse(null)
                                                                : null;

                                                String coffeeVariety = cd.getCoffeeVariety() != null
                                                                ? cd.getCoffeeVariety().getTranslations().stream()
                                                                                .filter(t -> t.getLanguage()
                                                                                                .equals(language))
                                                                                .findFirst()
                                                                                .map(CoffeeVarietyTranslation::getName)
                                                                                .orElse(null)
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
                                .originSlug(slug(product.getOrigin()))
                                .farmSlug(product.getFarm() != null
                                                ? product.getFarm().getSlug()
                                                : null)
                                .productType(product.getProductType())
                                .featured(product.isFeatured())
                                .isActive(product.isActive())
                                .name(translation != null ? translation.getName() : null)
                                .shortDescription(translation != null
                                                ? translation.getShortDescription()
                                                : null)
                                .description(translation != null
                                                ? translation.getDescription()
                                                : null)
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

        private List<ProductVariantResponse> buildVariants(Product product) {
                return productVariantRepository
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
        }

        private List<ProductMediaResponse> buildMedia(Product product) {
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

        private String slug(Origin origin) {
                return origin != null ? origin.getSlug() : null;
        }
}
