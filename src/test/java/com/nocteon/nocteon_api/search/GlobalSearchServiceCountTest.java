package com.nocteon.nocteon_api.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.category.repository.CategoryRepository;
import com.nocteon.nocteon_api.journal.repository.JournalPostRepository;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.promoCode.repository.PromoCodeRepository;
import com.nocteon.nocteon_api.search.dto.response.GlobalSearchResponse;
import com.nocteon.nocteon_api.search.service.GlobalSearchService;

/**
 * What the dashboard search reports, as distinct from what it matches.
 *
 * <p>Written against the count, which was the actual defect: the service capped each type at five
 * rows and then set {@code totalCount = results.size()}, so a query matching twelve products
 * announced five and the other seven became invisible. No count query existed anywhere in the
 * search package.
 *
 * <p>Matching itself is not what these pin. The translatable types inner-join translations with no
 * language predicate and users search with {@code ILIKE}, so an Arabic or English query both match;
 * that is a property of the repository queries and is exercised by the integration sweep rather
 * than by mocks.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlobalSearchServiceCountTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private PromoCodeRepository promoCodeRepository;
    @Mock
    private JournalPostRepository journalPostRepository;

    @InjectMocks
    private GlobalSearchService globalSearchService;

    /**
     * {@code count} products that the service can actually render, since the row query feeds a
     * mapping that reads translations and category rather than passing rows straight through.
     */
    private static List<Product> products(int count) {
        List<Product> products = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ProductTranslation translation = ProductTranslation.builder()
                    .language("en")
                    .name("Product " + i)
                    .build();
            products.add(Product.builder()
                    .id((long) i)
                    .slug("product-" + i)
                    .category(Category.builder().slug("coffee").build())
                    .translations(Set.of(translation))
                    .build());
        }
        return products;
    }

    private void stubRowsOnly() {
        when(orderRepository.searchOrders(anyString(), any())).thenReturn(List.of());
        when(userRepository.searchUsers(anyString(), any())).thenReturn(List.of());
        when(productRepository.searchProducts(anyString(), any())).thenReturn(List.of());
        when(categoryRepository.searchCategories(anyString(), any())).thenReturn(List.of());
        when(promoCodeRepository.searchPromoCodes(anyString(), any())).thenReturn(List.of());
        when(journalPostRepository.searchJournalPosts(anyString(), any())).thenReturn(List.of());
    }

    private void stubCountsOnly() {
        when(orderRepository.countSearchOrders(anyString())).thenReturn(0L);
        when(userRepository.countSearchUsers(anyString())).thenReturn(0L);
        when(productRepository.countSearchProducts(anyString())).thenReturn(0L);
        when(categoryRepository.countSearchCategories(anyString())).thenReturn(0L);
        when(promoCodeRepository.countSearchPromoCodes(anyString())).thenReturn(0L);
        when(journalPostRepository.countSearchJournalPosts(anyString())).thenReturn(0L);
    }

    @Test
    @DisplayName("totalCount is the number matched, not the number returned")
    void totalCountIsTheMatchedTotalNotTheCappedTotal() {
        stubRowsOnly();
        stubCountsOnly();
        when(productRepository.searchProducts(anyString(), any())).thenReturn(products(5));
        when(productRepository.countSearchProducts(anyString())).thenReturn(12L);

        GlobalSearchResponse response = globalSearchService.search("coffee");

        assertThat(response.getResults())
                .as("the per-type page stays capped, so the UI still has something to show")
                .hasSize(5);

        assertThat(response.getTotalCount())
                .as("twelve products matched; reporting five hides seven of them")
                .isEqualTo(12);
    }

    @Test
    @DisplayName("the per-type breakdown reports each type's own matched total")
    void countsByTypeReportsEachTypesTotal() {
        stubRowsOnly();
        stubCountsOnly();
        when(productRepository.countSearchProducts(anyString())).thenReturn(12L);
        when(categoryRepository.countSearchCategories(anyString())).thenReturn(3L);
        when(orderRepository.countSearchOrders(anyString())).thenReturn(1L);
        when(userRepository.countSearchUsers(anyString())).thenReturn(40L);

        GlobalSearchResponse response = globalSearchService.search("coffee");

        assertThat(response.getCountsByType())
                .as("keyed by SearchResultType.getRouteSegment() so the client gets a stable key")
                .containsEntry("products", 12)
                .containsEntry("categories", 3)
                .containsEntry("users", 40)
                .containsEntry("orders", 1)
                .containsEntry("promo-codes", 0)
                .containsEntry("journal-posts", 0);

        assertThat(response.getTotalCount())
                .as("the total sums across types, including the ones nothing was shown for")
                .isEqualTo(56);
    }

    /**
     * The case that made the old number wrong in the other direction: a type whose matches all
     * exceed the per-type cap, where every returned row counted as one and the rest vanished.
     */
    @Test
    @DisplayName("a type matching far more than the cap contributes its full count")
    void overCapTypeContributesItsFullCount() {
        stubRowsOnly();
        stubCountsOnly();
        when(userRepository.countSearchUsers(anyString())).thenReturn(137L);

        GlobalSearchResponse response = globalSearchService.search("al");

        assertThat(response.getTotalCount())
                .as("137 users match; the count must not collapse to the five returned")
                .isEqualTo(137);
    }

    @Test
    @DisplayName("a query too short to search returns nothing and touches no repository")
    void tooShortQueryReturnsNothing() {
        GlobalSearchResponse response = globalSearchService.search("a");

        assertThat(response.getResults()).isEmpty();
        assertThat(response.getTotalCount()).isZero();
        assertThat(response.getCountsByType()).isEmpty();

        verifyNoInteractions(productRepository, userRepository, categoryRepository,
                orderRepository, promoCodeRepository, journalPostRepository);
    }

    @Test
    @DisplayName("a blank or whitespace query returns nothing and touches no repository")
    void blankQueryReturnsNothing() {
        assertThat(globalSearchService.search(null).getTotalCount()).isZero();
        assertThat(globalSearchService.search("").getTotalCount()).isZero();
        assertThat(globalSearchService.search("   ").getTotalCount()).isZero();

        verifyNoInteractions(productRepository, userRepository, categoryRepository,
                orderRepository, promoCodeRepository, journalPostRepository);
    }
}