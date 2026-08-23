package com.nocteon.nocteon_api.product.dto.request;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;

import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.product.enums.GrindType;
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
    private List<Integer> weightGrams;
    private List<GrindType> grindTypes;
    private Boolean inStockOnly;
    private Boolean onSaleOnly;

    public Pageable toProductPageable() {
        return PageRequest.of(getPage(), getSize());
    }

}