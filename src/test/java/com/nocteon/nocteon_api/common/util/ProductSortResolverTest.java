package com.nocteon.nocteon_api.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;

/**
 * Sort resolution for the public product listing.
 *
 * <p>Written after QA mistook this class for a bug and nearly "fixed" it. The resolver returning
 * {@link Sort#unsorted()} is correct, and the test now pins that contract so the mistake is not made
 * twice.
 */
class ProductSortResolverTest {

    @Test
    @DisplayName("a plain product column is ordered, in the requested direction")
    void directFieldsAreOrdered() {
        assertThat(ProductSortResolver.resolve("createdAt", "asc")
                .getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.ASC);

        assertThat(ProductSortResolver.resolve("featured", "desc")
                .getOrderFor("featured").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    /** An unrecognised direction is not an error; it falls back to descending. */
    @Test
    @DisplayName("an unrecognised direction falls back to descending")
    void unknownDirectionFallsBackToDescending() {
        assertThat(ProductSortResolver.resolve("createdAt", "sideways")
                .getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    /**
     * The contract this class most needs to state.
     *
     * <p>{@code Sort.unsorted()} here means "do not override the Specification", not "return rows in
     * arbitrary order". {@code ProductSpecifications} orders {@code price} with {@code min}/{@code max}
     * over variants and {@code name} with {@code least}/{@code greatest} over the per-locale
     * translations; a Pageable sort replaces that, and both directions end up returning the same
     * page.
     *
     * <p>The failure this guards against is subtle: returning {@code createdAt DESC} as a fallback
     * looks like a strict improvement, passes an assertion that only checks the result is "sorted",
     * and silently disables aggregate ordering in production.
     */
    @ParameterizedTest(name = "sort={0} leaves the ordering to the Specification")
    @ValueSource(strings = { "name", "price", "nonexistentField", "", "createdAt; DROP TABLE products" })
    @DisplayName("a sort needing an aggregate resolves to unsorted, not to a fallback column")
    void aggregateSortsResolveToUnsorted(String sort) {
        assertThat(ProductSortResolver.resolve(sort, "asc").isSorted())
                .as("""
                        sort=%s must not set a Pageable sort.

                        ProductSpecifications orders price and name with aggregates over joined \
                        tables. A Pageable sort takes precedence over query.orderBy, so returning \
                        a fallback column here replaces that aggregate ordering and both directions \
                        then return identical pages.""")
                .isFalse();
    }
}