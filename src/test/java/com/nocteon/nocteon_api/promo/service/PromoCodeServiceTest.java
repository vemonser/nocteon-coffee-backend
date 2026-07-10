package com.nocteon.nocteon_api.promo.service;

import com.nocteon.nocteon_api.cart.entity.CartItem;
import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.promoCode.dto.request.PromoCodeRequest;
import com.nocteon.nocteon_api.promoCode.entity.PromoCode;
import com.nocteon.nocteon_api.promoCode.enums.PromoCodeDiscountType;
import com.nocteon.nocteon_api.promoCode.enums.PromoScopeType;
import com.nocteon.nocteon_api.promoCode.exceptions.PromoCodeAlreadyExistsException;
import com.nocteon.nocteon_api.promoCode.exceptions.PromoCodeAlreadyUsedException;
import com.nocteon.nocteon_api.promoCode.exceptions.PromoCodeCategoryRequiredException;
import com.nocteon.nocteon_api.promoCode.exceptions.PromoCodeExhaustedException;
import com.nocteon.nocteon_api.promoCode.exceptions.PromoCodeExpiredException;
import com.nocteon.nocteon_api.promoCode.exceptions.PromoCodeInactiveException;
import com.nocteon.nocteon_api.promoCode.exceptions.PromoCodeMinOrderNotMetException;
import com.nocteon.nocteon_api.promoCode.exceptions.PromoCodeNotApplicableException;
import com.nocteon.nocteon_api.promoCode.exceptions.PromoCodeNotFoundException;
import com.nocteon.nocteon_api.promoCode.repository.PromoCodeRedemptionRepository;
import com.nocteon.nocteon_api.promoCode.repository.PromoCodeRepository;
import com.nocteon.nocteon_api.promoCode.service.PromoCodeService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Edge-case coverage for PromoCodeService discount calculation and validation.
 * Covers: expiry, activation, min-order (global vs category scope), redemption
 * caps (total + per-user), scope filtering, and discount-type math.
 */
@ExtendWith(MockitoExtension.class)
class PromoCodeServiceTest {

    @Mock private PromoCodeRepository promoCodeRepository;
    @Mock private PromoCodeRedemptionRepository redemptionRepository;
    @Mock private com.nocteon.nocteon_api.category.repository.CategoryRepository categoryRepository;
    @Mock private com.nocteon.nocteon_api.cart.repository.CartRepository cartRepository;

    @InjectMocks
    private PromoCodeService promoCodeService;

    private Category coffeeCategory;
    private Category equipmentCategory;
    private CartItem coffeeItem;
    private CartItem equipmentItem;

    @BeforeEach
    void setUp() {
        coffeeCategory = Category.builder().id(1L).slug("coffee").build();
        equipmentCategory = Category.builder().id(2L).slug("equipment").build();

        Product coffeeProduct = Product.builder().id(10L).category(coffeeCategory).build();
        Product equipmentProduct = Product.builder().id(11L).category(equipmentCategory).build();

        ProductVariant coffeeVariant = ProductVariant.builder()
                .id(100L).product(coffeeProduct).price(BigDecimal.valueOf(300)).build();
        ProductVariant equipmentVariant = ProductVariant.builder()
                .id(101L).product(equipmentProduct).price(BigDecimal.valueOf(200)).build();

        coffeeItem = CartItem.builder().productVariant(coffeeVariant).quantity(1).build();
        equipmentItem = CartItem.builder().productVariant(equipmentVariant).quantity(1).build();
    }

    private PromoCode.PromoCodeBuilder<?, ?> validGlobalCodeBuilder() {
        Instant now = Instant.now();
        return PromoCode.builder()
                .id(1L)
                .code("WELCOME10")
                .discountType(PromoCodeDiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(10))
                .scopeType(PromoScopeType.GLOBAL)
                .maxRedemptionsPerUser(1)
                .active(true)
                .validFrom(now.minus(1, ChronoUnit.DAYS))
                .validUntil(now.plus(1, ChronoUnit.DAYS));
    }

    // ───────────────────────── Existence / activation / expiry ─────────────────────────

    @Nested
    class BasicValidation {

        @Test
        void throwsWhenCodeDoesNotExist() {
            when(promoCodeRepository.findByCodeIgnoreCase("MISSING")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> promoCodeService.calculateDiscountForCart(
                    "MISSING", List.of(coffeeItem), BigDecimal.valueOf(300), 1L))
                    .isInstanceOf(PromoCodeNotFoundException.class);
        }

        @Test
        void throwsWhenCodeIsInactive() {
            PromoCode code = validGlobalCodeBuilder().active(false).build();
            when(promoCodeRepository.findByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(code));

            assertThatThrownBy(() -> promoCodeService.calculateDiscountForCart(
                    "WELCOME10", List.of(coffeeItem), BigDecimal.valueOf(300), 1L))
                    .isInstanceOf(PromoCodeInactiveException.class);
        }

        @Test
        void throwsWhenCodeNotYetValid() {
            PromoCode code = validGlobalCodeBuilder()
                    .validFrom(Instant.now().plus(1, ChronoUnit.DAYS))
                    .validUntil(Instant.now().plus(2, ChronoUnit.DAYS))
                    .build();
            when(promoCodeRepository.findByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(code));

            assertThatThrownBy(() -> promoCodeService.calculateDiscountForCart(
                    "WELCOME10", List.of(coffeeItem), BigDecimal.valueOf(300), 1L))
                    .isInstanceOf(PromoCodeExpiredException.class);
        }

        @Test
        void throwsWhenCodeAlreadyExpired() {
            PromoCode code = validGlobalCodeBuilder()
                    .validFrom(Instant.now().minus(10, ChronoUnit.DAYS))
                    .validUntil(Instant.now().minus(1, ChronoUnit.DAYS))
                    .build();
            when(promoCodeRepository.findByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(code));

            assertThatThrownBy(() -> promoCodeService.calculateDiscountForCart(
                    "WELCOME10", List.of(coffeeItem), BigDecimal.valueOf(300), 1L))
                    .isInstanceOf(PromoCodeExpiredException.class);
        }

        @Test
        void boundaryValidAtExactValidFromInstant() {
            Instant now = Instant.now();
            PromoCode code = validGlobalCodeBuilder()
                    .validFrom(now.minus(1, ChronoUnit.SECONDS)) // just started
                    .validUntil(now.plus(1, ChronoUnit.DAYS))
                    .build();
            when(promoCodeRepository.findByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(code));
            lenient().when(redemptionRepository.countByPromoCodeIdAndUserId(1L, 1L)).thenReturn(0L);

            var result = promoCodeService.calculateDiscountForCart(
                    "WELCOME10", List.of(coffeeItem), BigDecimal.valueOf(300), 1L);

            assertThat(result).isNotNull();
        }
    }

    // ───────────────────────── Scope filtering (GLOBAL vs CATEGORY) ─────────────────────────

    @Nested
    class ScopeFiltering {

        @Test
        void globalScopeAppliesToAllItems() {
            PromoCode code = validGlobalCodeBuilder().build();
            when(promoCodeRepository.findByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(code));
            lenient().when(redemptionRepository.countByPromoCodeIdAndUserId(1L, 1L)).thenReturn(0L);

            var result = promoCodeService.calculateDiscountForCart(
                    "WELCOME10", List.of(coffeeItem, equipmentItem), BigDecimal.valueOf(500), 1L);

            // 10% of (300 + 200) = 50
            assertThat(result.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(50.00));
        }

        @Test
        void categoryScopeOnlyDiscountsMatchingItems() {
            PromoCode code = validGlobalCodeBuilder()
                    .scopeType(PromoScopeType.CATEGORY)
                    .categories(List.of(coffeeCategory))
                    .build();
            when(promoCodeRepository.findByCodeIgnoreCase("COFFEE10")).thenReturn(Optional.of(code));
            lenient().when(redemptionRepository.countByPromoCodeIdAndUserId(1L, 1L)).thenReturn(0L);

            var result = promoCodeService.calculateDiscountForCart(
                    "COFFEE10", List.of(coffeeItem, equipmentItem), BigDecimal.valueOf(500), 1L);

            // 10% of coffee item only (300) = 30, equipment (200) untouched
            assertThat(result.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(30.00));
        }

        @Test
        void throwsNotApplicableWhenNoItemsMatchCategoryScope() {
            PromoCode code = validGlobalCodeBuilder()
                    .scopeType(PromoScopeType.CATEGORY)
                    .categories(List.of(equipmentCategory))
                    .build();
            when(promoCodeRepository.findByCodeIgnoreCase("EQUIP10")).thenReturn(Optional.of(code));

            // Cart only has coffee, code targets equipment only
            assertThatThrownBy(() -> promoCodeService.calculateDiscountForCart(
                    "EQUIP10", List.of(coffeeItem), BigDecimal.valueOf(300), 1L))
                    .isInstanceOf(PromoCodeNotApplicableException.class);
        }
    }

    // ───────────────────────── Min order amount: global total vs eligible amount ─────────────────────────

    @Nested
    class MinOrderAmountRules {

        @Test
        void globalScopeChecksMinOrderAgainstFullCartTotal() {
            PromoCode code = validGlobalCodeBuilder()
                    .minOrderAmount(BigDecimal.valueOf(400))
                    .build();
            when(promoCodeRepository.findByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(code));

            // full cart total 300 < 400 -> should fail even though it's GLOBAL scope
            assertThatThrownBy(() -> promoCodeService.calculateDiscountForCart(
                    "WELCOME10", List.of(coffeeItem), BigDecimal.valueOf(300), 1L))
                    .isInstanceOf(PromoCodeMinOrderNotMetException.class);
        }

        @Test
        void categoryScopeChecksMinOrderAgainstEligibleAmountOnly() {
            // Coupon targets equipment, min order 500 -> but only eligible (equipment) amount counts,
            // NOT the full cart total (which includes coffee too).
            PromoCode code = validGlobalCodeBuilder()
                    .scopeType(PromoScopeType.CATEGORY)
                    .categories(List.of(equipmentCategory))
                    .minOrderAmount(BigDecimal.valueOf(500))
                    .build();
            when(promoCodeRepository.findByCodeIgnoreCase("EQUIP10")).thenReturn(Optional.of(code));

            // full cart total = 500 (300 coffee + 200 equipment), but eligible (equipment) = 200 < 500
            assertThatThrownBy(() -> promoCodeService.calculateDiscountForCart(
                    "EQUIP10", List.of(coffeeItem, equipmentItem), BigDecimal.valueOf(500), 1L))
                    .isInstanceOf(PromoCodeMinOrderNotMetException.class);
        }

        @Test
        void categoryScopeCannotBeGamedByPaddingCartWithUnrelatedItems() {
            // Regression test for the exact exploit scenario discussed:
            // cheap equipment + expensive unrelated coffee should NOT unlock the equipment discount.
            ProductVariant cheapEquipmentVariant = ProductVariant.builder()
                    .id(102L)
                    .product(Product.builder().id(12L).category(equipmentCategory).build())
                    .price(BigDecimal.valueOf(10))
                    .build();
            CartItem cheapEquipmentItem = CartItem.builder().productVariant(cheapEquipmentVariant).quantity(1).build();

            PromoCode code = validGlobalCodeBuilder()
                    .scopeType(PromoScopeType.CATEGORY)
                    .categories(List.of(equipmentCategory))
                    .minOrderAmount(BigDecimal.valueOf(500))
                    .build();
            when(promoCodeRepository.findByCodeIgnoreCase("EQUIP10")).thenReturn(Optional.of(code));

            // cart total = 490 (coffee) + 10 (equipment) = 500, but eligible (equipment) amount = 10
            assertThatThrownBy(() -> promoCodeService.calculateDiscountForCart(
                    "EQUIP10", List.of(coffeeItem, cheapEquipmentItem), BigDecimal.valueOf(500), 1L))
                    .isInstanceOf(PromoCodeMinOrderNotMetException.class);
        }
    }

    // ───────────────────────── Redemption caps ─────────────────────────

    @Nested
    class RedemptionCaps {

        @Test
        void throwsWhenMaxTotalRedemptionsExhausted() {
            PromoCode code = validGlobalCodeBuilder()
                    .maxTotalRedemptions(100)
                    .build();
            when(promoCodeRepository.findByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(code));
            when(redemptionRepository.countByPromoCodeId(1L)).thenReturn(100L);

            assertThatThrownBy(() -> promoCodeService.calculateDiscountForCart(
                    "WELCOME10", List.of(coffeeItem), BigDecimal.valueOf(300), 1L))
                    .isInstanceOf(PromoCodeExhaustedException.class);
        }

        @Test
        void allowsRedemptionOneBelowMaxTotalBoundary() {
            PromoCode code = validGlobalCodeBuilder()
                    .maxTotalRedemptions(100)
                    .build();
            when(promoCodeRepository.findByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(code));
            when(redemptionRepository.countByPromoCodeId(1L)).thenReturn(99L);
            when(redemptionRepository.countByPromoCodeIdAndUserId(1L, 1L)).thenReturn(0L);

            var result = promoCodeService.calculateDiscountForCart(
                    "WELCOME10", List.of(coffeeItem), BigDecimal.valueOf(300), 1L);

            assertThat(result).isNotNull();
        }

        @Test
        void throwsWhenUserAlreadyUsedCodeMaxAllowedTimes() {
            PromoCode code = validGlobalCodeBuilder()
                    .maxRedemptionsPerUser(1)
                    .build();
            when(promoCodeRepository.findByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(code));
            when(redemptionRepository.countByPromoCodeIdAndUserId(1L, 1L)).thenReturn(1L);

            assertThatThrownBy(() -> promoCodeService.calculateDiscountForCart(
                    "WELCOME10", List.of(coffeeItem), BigDecimal.valueOf(300), 1L))
                    .isInstanceOf(PromoCodeAlreadyUsedException.class);
        }

        @Test
        void differentUserCanStillUseCodeAfterFirstUserExhaustedTheirLimit() {
            PromoCode code = validGlobalCodeBuilder()
                    .maxRedemptionsPerUser(1)
                    .build();
            when(promoCodeRepository.findByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(code));
            // user 2 has never redeemed
            when(redemptionRepository.countByPromoCodeIdAndUserId(1L, 2L)).thenReturn(0L);

            var result = promoCodeService.calculateDiscountForCart(
                    "WELCOME10", List.of(coffeeItem), BigDecimal.valueOf(300), 2L);

            assertThat(result).isNotNull();
        }
    }

    // ───────────────────────── Discount type math ─────────────────────────

    @Nested
    class DiscountTypeCalculations {

        @Test
        void percentageDiscountRoundsToTwoDecimals() {
            PromoCode code = validGlobalCodeBuilder()
                    .discountType(PromoCodeDiscountType.PERCENTAGE)
                    .discountValue(BigDecimal.valueOf(33.333))
                    .build();
            when(promoCodeRepository.findByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(code));
            lenient().when(redemptionRepository.countByPromoCodeIdAndUserId(1L, 1L)).thenReturn(0L);

            var result = promoCodeService.calculateDiscountForCart(
                    "WELCOME10", List.of(coffeeItem), BigDecimal.valueOf(300), 1L);

            // 300 * 33.333% = 99.999 -> rounds to 100.00 (HALF_UP)
            assertThat(result.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
        }

        @Test
        void fixedAmountDiscountIsCappedAtEligibleAmountNotExceedingIt() {
            // Fixed discount of 500 on eligible amount of only 300 -> capped at 300, never negative total
            PromoCode code = validGlobalCodeBuilder()
                    .discountType(PromoCodeDiscountType.FIXED_AMOUNT)
                    .discountValue(BigDecimal.valueOf(500))
                    .build();
            when(promoCodeRepository.findByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(code));
            lenient().when(redemptionRepository.countByPromoCodeIdAndUserId(1L, 1L)).thenReturn(0L);

            var result = promoCodeService.calculateDiscountForCart(
                    "WELCOME10", List.of(coffeeItem), BigDecimal.valueOf(300), 1L);

            assertThat(result.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(300));
        }

        @Test
        void fixedAmountDiscountBelowEligibleAmountAppliesInFull() {
            PromoCode code = validGlobalCodeBuilder()
                    .discountType(PromoCodeDiscountType.FIXED_AMOUNT)
                    .discountValue(BigDecimal.valueOf(50))
                    .build();
            when(promoCodeRepository.findByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(code));
            lenient().when(redemptionRepository.countByPromoCodeIdAndUserId(1L, 1L)).thenReturn(0L);

            var result = promoCodeService.calculateDiscountForCart(
                    "WELCOME10", List.of(coffeeItem), BigDecimal.valueOf(300), 1L);

            assertThat(result.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(50));
        }

        @Test
        void freeShippingTypeReturnsZeroDiscountButFlagsFreeShippingTrue() {
            PromoCode code = validGlobalCodeBuilder()
                    .discountType(PromoCodeDiscountType.FREE_SHIPPING)
                    .discountValue(null)
                    .build();
            when(promoCodeRepository.findByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(code));
            lenient().when(redemptionRepository.countByPromoCodeIdAndUserId(1L, 1L)).thenReturn(0L);

            var result = promoCodeService.calculateDiscountForCart(
                    "WELCOME10", List.of(coffeeItem), BigDecimal.valueOf(300), 1L);

            assertThat(result.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.isFreeShipping()).isTrue();
        }
    }

    // ───────────────────────── Category scope requires at least one category ─────────────────────────

    @Nested
    class CategoryRequirementOnCreate {

        @Test
        void createThrowsWhenCategoryScopeHasNoCategoriesSelected() {
            var request = new PromoCodeRequest();
            request.setCode("BADCODE");
            request.setDiscountType(PromoCodeDiscountType.PERCENTAGE);
            request.setDiscountValue(BigDecimal.TEN);
            request.setScopeType(PromoScopeType.CATEGORY);
            request.setCategorySlugs(List.of()); // empty - should be rejected
            request.setValidFrom(Instant.now());
            request.setValidUntil(Instant.now().plus(1, ChronoUnit.DAYS));

            when(promoCodeRepository.existsByCodeIgnoreCase("BADCODE")).thenReturn(false);

            assertThatThrownBy(() -> promoCodeService.create(request))
                    .isInstanceOf(PromoCodeCategoryRequiredException.class);
        }

        @Test
        void createThrowsWhenCodeAlreadyExists() {
            var request = new PromoCodeRequest();
            request.setCode("WELCOME10");
            request.setDiscountType(PromoCodeDiscountType.PERCENTAGE);
            request.setDiscountValue(BigDecimal.TEN);
            request.setScopeType(PromoScopeType.GLOBAL);
            request.setValidFrom(Instant.now());
            request.setValidUntil(Instant.now().plus(1, ChronoUnit.DAYS));

            when(promoCodeRepository.existsByCodeIgnoreCase("WELCOME10")).thenReturn(true);

            assertThatThrownBy(() -> promoCodeService.create(request))
                    .isInstanceOf(PromoCodeAlreadyExistsException.class);
        }
    }
}