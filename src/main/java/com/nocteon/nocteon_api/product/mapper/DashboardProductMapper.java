package com.nocteon.nocteon_api.product.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethodTranslation;
import com.nocteon.nocteon_api.product.dto.response.DashboardCoffeeDetailsResponse;
import com.nocteon.nocteon_api.product.dto.response.DashboardProductResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductBrewingMethodResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductMediaResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductTranslationResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductVariantResponse;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.repository.CoffeeDetailsRepository;
import com.nocteon.nocteon_api.product.repository.ProductBrewingMethodRepository;
import com.nocteon.nocteon_api.product.repository.ProductTranslationRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DashboardProductMapper {

        private final ProductTranslationRepository productTranslationRepository;
        private final ProductBrewingMethodRepository productBrewingMethodRepository;
        private final CoffeeDetailsRepository coffeeDetailsRepository;
        private final ProductResponseMapper productResponseMapper;

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

                List<ProductVariantResponse> variants = productResponseMapper.buildVariants(product);
                List<ProductMediaResponse> media = productResponseMapper.buildMedia(product);

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
                                .map(tn -> tn.getSlug())
                                .toList();

                List<String> pairings = product.getPairings().stream()
                                .map(p -> p.getSlug())
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

                DashboardCoffeeDetailsResponse coffeeDetailsResponse = coffeeDetailsRepository
                                .findByProductId(product.getId())
                                .map(cd -> DashboardCoffeeDetailsResponse.builder()
                                                .processingMethodSlug(cd.getProcessingMethod() != null
                                                                ? cd.getProcessingMethod().getSlug()
                                                                : null)
                                                .coffeeVarietySlug(cd.getCoffeeVariety() != null
                                                                ? cd.getCoffeeVariety().getSlug()
                                                                : null)
                                                .altitude(cd.getAltitude())
                                                .harvestYear(cd.getHarvestYear())
                                                .story(cd.getStory())
                                                .roastLevelSlug(cd.getRoastLevel() != null
                                                                ? cd.getRoastLevel().getSlug()
                                                                : null)
                                                .build())
                                .orElse(null);

                return DashboardProductResponse.builder()
                                .id(product.getId())
                                .slug(product.getSlug())
                                .categorySlug(product.getCategory().getSlug())
                                .originSlug(product.getOrigin() != null
                                                ? product.getOrigin().getSlug()
                                                : null)
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
                                .translations(translations)
                                .coffeeDetails(coffeeDetailsResponse)
                                .variants(variants)
                                .media(media)
                                .tastingNotes(tastingNotes)
                                .pairings(pairings)
                                .brewingMethods(brewingMethods)
                                .build();
        }
}
