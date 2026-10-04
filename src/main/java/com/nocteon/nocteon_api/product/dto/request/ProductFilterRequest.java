package com.nocteon.nocteon_api.product.dto.request;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;

import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.product.enums.ProductType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductFilterRequest extends BaseFilterRequest {
    private String search;
    private String categorySlug;
    private String originSlug;
    private String farmSlug;
    private Double minRating;
    private ProductType productType;
    private Boolean featured;
    private Boolean isActive;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    private String brewingMethodSlug;
    private List<Long> optionValueIds;
    private Boolean inStockOnly;
    private Boolean onSaleOnly;

    /**
     * Opt-in flag for vector-similarity ranking, bound from {@code ?semantic=true}.
     *
     * <p>Null and false mean the same thing: plain keyword search. No validation annotation,
     * because the controller binds this object with {@code @ModelAttribute} and does not
     * {@code @Valid} it.
     */
    private Boolean semantic;

    public Pageable toProductPageable() {
        return PageRequest.of(getPage(), getSize());
    }
}