package com.nocteon.nocteon_api.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class DiscountCalculator {
    private DiscountCalculator() {
    }

    public static Integer calculate(BigDecimal price, BigDecimal compareAtPrice) {
        if (compareAtPrice == null || price == null) {
            return null;
        }

        if (compareAtPrice.compareTo(price) <= 0) {
            return null;
        }

        return compareAtPrice
                .subtract(price)
                .multiply(BigDecimal.valueOf(100))
                .divide(compareAtPrice, 0, RoundingMode.HALF_UP)
                .intValue();
    }

}
