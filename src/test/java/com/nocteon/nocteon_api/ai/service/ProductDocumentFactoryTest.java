package com.nocteon.nocteon_api.ai.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethod;
import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVariety;
import com.nocteon.nocteon_api.farm.entity.Farm;
import com.nocteon.nocteon_api.origin.entity.Origin;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethod;
import com.nocteon.nocteon_api.product.entity.CoffeeDetails;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductBrewingMethod;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevel;
import com.nocteon.nocteon_api.tastingNote.entity.TastingNote;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ProductDocumentFactoryTest {

    private static final String PRODUCT_SLUG = "yirgacheffe";

    private final ProductDocumentFactory factory = new ProductDocumentFactory();

    private Product coffeeProduct(String enName, String arName) {
        Set<ProductTranslation> translations = new LinkedHashSet<>();
        translations.add(ProductTranslation.builder()
                .language("en").name(enName)
                .shortDescription("Floral and bright")
                .build());
        if (arName != null) {
            translations.add(ProductTranslation.builder()
                    .language("ar").name(arName)
                    .shortDescription("زهري ومشرق")
                    .build());
        }

        return Product.builder()
                .id(7L)
                .slug(PRODUCT_SLUG)
                .productType(ProductType.COFFEE)
                .category(Category.builder().id(1L).slug("single-origin").build())
                .origin(Origin.builder().id(2L).slug("ethiopia").build())
                .farm(Farm.builder().id(3L).slug("kochere-farm").build())
                .translations(translations)
                .coffeeDetails(CoffeeDetails.builder()
                        .id(9L)
                        .roastLevel(RoastLevel.builder().id(4L).slug("light-roast").build())
                        .processingMethod(ProcessingMethod.builder().id(5L).slug("washed").build())
                        .coffeeVariety(CoffeeVariety.builder().id(6L).slug("heirloom").build())
                        .build())
                .tastingNotes(List.of(
                        TastingNote.builder().id(8L).slug("bergamot").build(),
                        TastingNote.builder().id(9L).slug("jasmine").build()))
                .brewingMethods(List.of(
                        ProductBrewingMethod.builder()
                                .brewingMethod(BrewingMethod.builder().id(1L).slug("pour-over").build())
                                .score(9)
                                .build(),
                        ProductBrewingMethod.builder()
                                .brewingMethod(BrewingMethod.builder().id(2L).slug("espresso").build())
                                .score(6)
                                .build()))
                .build();
    }

    @Test
    void includesEnglishAndArabicNamesInText() {
        Product p = coffeeProduct("Yirgacheffe", "يرغاتشيف");
        String text = factory.buildText(p);

        assertThat(text).contains("Yirgacheffe");
        assertThat(text).contains("يرغاتشيف");
    }

    @Test
    void fallsBackWhenArabicTranslationMissing() {
        // Review Focus item 5: only 'en' exists.
        Product p = coffeeProduct("Yirgacheffe", null);
        String text = factory.buildText(p);

        assertThat(text).contains("Yirgacheffe");
    }

    @Test
    void labelsEveryTranslationByItsOwnLanguage() {
        // No hard-coded 'en'/'ar' keys: a third language is indexed without a code change.
        Product p = coffeeProduct("Yirgacheffe", "يرغاتشيف");
        p.getTranslations().add(ProductTranslation.builder()
                .language("fr").name("Yirgacheffe Éthiopie")
                .build());

        String text = factory.buildText(p);

        assertThat(text).contains("en");
        assertThat(text).contains("ar");
        assertThat(text).contains("fr");
        assertThat(text).contains("Yirgacheffe Éthiopie");
    }

    @Test
    void nonCoffeeProductStillProducesTextWithoutNullFields() {
        // Review Focus item 2: productType EQUIPMENT (non-coffee), coffeeDetails null.
        Product p = Product.builder()
                .id(11L).slug("ceramic-mug").productType(ProductType.EQUIPMENT)
                .translations(Set.of(ProductTranslation.builder()
                        .language("en").name("Ceramic Mug")
                        .shortDescription("Holds 300ml").build()))
                .build();

        String text = factory.buildText(p);

        assertThat(text).contains("Ceramic Mug");
        assertThat(text).contains("Holds 300ml");
    }

    @Test
    void productWithOnlyTranslationsAndNoRelationsStillProducesText() {
        Product p = Product.builder()
                .id(12L).slug("mystery-box").productType(ProductType.EQUIPMENT)
                .translations(Set.of(ProductTranslation.builder()
                        .language("en").name("Mystery Box").build()))
                .build();

        assertThat(factory.buildText(p)).isNotBlank().contains("Mystery Box");
    }

    @Test
    void coffeeDetailsWithEveryRelationNullDoesNotThrow() {
        Product p = Product.builder()
                .id(13L).slug("bare-coffee").productType(ProductType.COFFEE)
                .translations(Set.of(ProductTranslation.builder()
                        .language("en").name("Bare Coffee").build()))
                .coffeeDetails(CoffeeDetails.builder().id(14L).build())
                .build();

        assertThat(factory.buildText(p)).contains("Bare Coffee");
    }

    @Test
    void usesRelationSlugsForBrewingScoresAndTastingNotes() {
        String text = factory.buildText(coffeeProduct("Yirgacheffe", null));

        assertThat(text).contains("single-origin");
        assertThat(text).contains("ethiopia");
        assertThat(text).contains("kochere-farm");
        assertThat(text).contains("light-roast");
        assertThat(text).contains("washed");
        assertThat(text).contains("heirloom");
        assertThat(text).contains("bergamot");
        assertThat(text).contains("jasmine");
        assertThat(text).contains("pour-over:9");
        assertThat(text).contains("espresso:6");
    }

    @Test
    void metadataCarriesIdentityAndContentHash() {
        Product p = coffeeProduct("Yirgacheffe", "يرغاتشيف");
        Map<String, Object> meta = factory.buildMetadata(p);

        assertThat(meta).containsEntry("productId", 7L)
                .containsEntry("slug", PRODUCT_SLUG)
                .containsEntry("categorySlug", "single-origin")
                .containsEntry("originSlug", "ethiopia")
                .containsKey("contentHash");
    }

    @Test
    void originSlugIsAbsentWhenProductHasNoOrigin() {
        Product p = Product.builder()
                .id(15L).slug("no-origin").productType(ProductType.EQUIPMENT)
                .translations(Set.of(ProductTranslation.builder()
                        .language("en").name("No Origin").build()))
                .build();

        assertThat(factory.buildMetadata(p)).doesNotContainKey("originSlug");
    }

    @Test
    void contentHashChangesWhenTextChanges() {
        Object a = factory.buildMetadata(coffeeProduct("A", null)).get("contentHash");
        Object b = factory.buildMetadata(coffeeProduct("B", null)).get("contentHash");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void contentHashIsStableAcrossCalls() {
        Product p = coffeeProduct("Yirgacheffe", null);
        assertThat(factory.buildMetadata(p).get("contentHash"))
                .isEqualTo(factory.buildMetadata(p).get("contentHash"));
    }

    @Test
    void toDocumentComposesSlugPrefixedIdTextAndMetadata() {
        Product p = coffeeProduct("Yirgacheffe", "يرغاتشيف");

        Document document = factory.toDocument(p);

        assertThat(document.getId()).isEqualTo("product:" + PRODUCT_SLUG);
        assertThat(document.getText()).isEqualTo(factory.buildText(p));
        assertThat(document.getMetadata()).isEqualTo(factory.buildMetadata(p));
    }

    @Test
    void toDocumentWorksForNonCoffeeProduct() {
        Product p = Product.builder()
                .id(16L).slug("steel-tamper").productType(ProductType.EQUIPMENT)
                .translations(new LinkedHashSet<>(List.of(ProductTranslation.builder()
                        .language("en").name("Steel Tamper").build())))
                .tastingNotes(new ArrayList<>())
                .build();

        Document document = factory.toDocument(p);

        assertThat(document.getId()).isEqualTo("product:steel-tamper");
        assertThat(document.getText()).contains("Steel Tamper");
    }
}