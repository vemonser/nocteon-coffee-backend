package com.nocteon.nocteon_api.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nocteon.nocteon_api.brewingMethod.repository.BrewingMethodRepository;
import com.nocteon.nocteon_api.common.exception.invalid.RelatedProductSelfReferenceException;
import com.nocteon.nocteon_api.common.exception.invalid.TooManyRelatedProductsException;
import com.nocteon.nocteon_api.common.exception.notFound.ProductNotFoundException;
import com.nocteon.nocteon_api.pairing.repository.PairingRepository;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.repository.ProductBrewingMethodRepository;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.tastingNote.repository.TastingNoteRepository;

/**
 * FE-11: what "You may also like" refuses.
 *
 * <p>All three refusals matter because the alternative is silent: a seventh entry truncated, a
 * self-reference rendering a link to the page you are on, or an unknown slug dropped so the admin
 * believes it was saved. The order is also asserted, because the admin picked it.
 */
@ExtendWith(MockitoExtension.class)
class ProductRelationServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private TastingNoteRepository tastingNoteRepository;
    @Mock
    private PairingRepository pairingRepository;
    @Mock
    private ProductBrewingMethodRepository productBrewingMethodRepository;
    @Mock
    private BrewingMethodRepository brewingMethodRepository;

    private ProductRelationService service() {
        return new ProductRelationService(
                productRepository, tastingNoteRepository, pairingRepository,
                productBrewingMethodRepository, brewingMethodRepository);
    }

    @Test
    void keepsTheOrderTheAdminPicked() {
        Product product = product("brazil-mogiana");
        when(productRepository.findBySlugIn(List.of("kenya-nyeri", "ethiopia-yirgacheffe")))
                .thenReturn(List.of(product("ethiopia-yirgacheffe"), product("kenya-nyeri")));

        service().replaceRelatedProducts(product, List.of("kenya-nyeri", "ethiopia-yirgacheffe"));

        assertThat(product.getRelatedProducts())
                .extracting(Product::getSlug)
                .containsExactly("kenya-nyeri", "ethiopia-yirgacheffe");
    }

    @Test
    void refusesASeventhEntry() {
        Product product = product("brazil-mogiana");

        assertThatThrownBy(() -> service().replaceRelatedProducts(product, List.of(
                "a", "b", "c", "d", "e", "f", "g")))
                .isInstanceOf(TooManyRelatedProductsException.class);
    }

    @Test
    void refusesTheProductItself() {
        Product product = product("brazil-mogiana");

        assertThatThrownBy(() -> service().replaceRelatedProducts(product, List.of("brazil-mogiana")))
                .isInstanceOf(RelatedProductSelfReferenceException.class);
    }

    @Test
    void refusesASlugThatDoesNotExist() {
        Product product = product("brazil-mogiana");
        when(productRepository.findBySlugIn(List.of("ghost"))).thenReturn(List.of());

        assertThatThrownBy(() -> service().replaceRelatedProducts(product, List.of("ghost")))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void anEmptySelectionClearsTheList() {
        Product product = product("brazil-mogiana");
        product.getRelatedProducts().add(product("kenya-nyeri"));

        service().replaceRelatedProducts(product, List.of());

        assertThat(product.getRelatedProducts()).isEmpty();
    }

    private static Product product(String slug) {
        return Product.builder()
                .id((long) slug.hashCode())
                .slug(slug)
                .relatedProducts(new ArrayList<>())
                .build();
    }
}
