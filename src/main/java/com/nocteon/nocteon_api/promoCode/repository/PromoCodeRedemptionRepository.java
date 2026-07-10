package com.nocteon.nocteon_api.promoCode.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.promoCode.entity.PromoCodeRedemption;

public interface PromoCodeRedemptionRepository extends JpaRepository<PromoCodeRedemption, Long> {

    long countByPromoCodeId(Long promoCodeId);

    long countByPromoCodeIdAndUserId(Long promoCodeId, Long userId);

    boolean existsByOrderId(Long orderId);

    @Query("SELECT COALESCE(SUM(r.discountAmount), 0) FROM PromoCodeRedemption r WHERE r.promoCode.id = :promoCodeId")
    BigDecimal sumDiscountByPromoCodeId(@Param("promoCodeId") Long promoCodeId);

    @Query("SELECT COALESCE(SUM(r.discountAmount), 0) FROM PromoCodeRedemption r")
    BigDecimal sumAllDiscountGiven();

    @Query("""
                SELECT r.promoCode.code, COUNT(r), SUM(r.discountAmount)
                FROM PromoCodeRedemption r
                GROUP BY r.promoCode.code
                ORDER BY COUNT(r) DESC
            """)
    List<Object[]> getTopPerformingCodes(Pageable pageable);

    @Query(value = """
                SELECT CAST(r.created_at AS DATE), COUNT(*), SUM(r.discount_amount)
                FROM promo_code_redemptions r
                WHERE r.created_at >= :since
                GROUP BY CAST(r.created_at AS DATE)
                ORDER BY CAST(r.created_at AS DATE)
            """, nativeQuery = true)
    List<Object[]> getRedemptionsByDay(@Param("since") Instant since);

}