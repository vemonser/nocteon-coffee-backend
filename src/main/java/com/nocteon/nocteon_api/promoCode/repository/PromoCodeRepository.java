package com.nocteon.nocteon_api.promoCode.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.promoCode.entity.PromoCode;

public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {

    @Query("""
                    SELECT pc FROM PromoCode pc
                    WHERE LOWER(pc.code) LIKE :query
                    ORDER BY CASE
                            WHEN LOWER(pc.code) = LOWER(:query) THEN 0
                            WHEN LOWER(pc.code) LIKE :query THEN 1
                            ELSE 2
                        END,
                        LOWER(pc.code)
                    """)
            /**
     * Exact code first, then prefix, then anywhere.
     *
     * <p>An exact code is overwhelmingly the common case for this type — an admin pastes a code
     * from an email — so it has to sort above codes that merely start with the same characters.
     */
List<PromoCode> searchPromoCodes(@Param("query") String query, Pageable pageable);

        /**
         * How many rows the search query matches, uncapped.
         *
         * <p>The search takes a fixed-size page of rows, so the returned list cannot be used
         * to report a total: it is the page size, not the match count. This mirrors the row
         * query's predicates exactly so the two cannot drift apart.
         */
        @Query("""
                        SELECT COUNT(pc) FROM PromoCode pc WHERE LOWER(pc.code) LIKE :query
                        """)
        long countSearchPromoCodes(@Param("query") String query);

    long countByActiveTrue();

    Optional<PromoCode> findByCodeIgnoreCase(String code);

    /**
     * Locked lookup for the checkout path. Validation and redemption happen in
     * the same transaction, so holding a write lock from the usage-count check
     * until commit serializes concurrent checkouts on the same code — two
     * buyers presenting the last redemption cannot both slip through.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pc FROM PromoCode pc WHERE LOWER(pc.code) = LOWER(:code)")
    Optional<PromoCode> findByCodeIgnoreCaseForUpdate(@Param("code") String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query(value = "SELECT e.* FROM promo_codes e WHERE e.deleted_at IS NOT NULL", nativeQuery = true)
    List<PromoCode> findAllDeletedNative();

    @Query(value = "SELECT e.* FROM promo_codes e WHERE e.deleted_at IS NOT NULL AND (COALESCE(:search, '') = '' OR LOWER(e.code) LIKE :search)", nativeQuery = true)
    List<PromoCode> findDeletedBySearchNative(@Param("search") String search);

    @Query(value = "SELECT e.* FROM promo_codes e WHERE e.id = :id AND e.deleted_at IS NOT NULL", nativeQuery = true)
    Optional<PromoCode> findDeletedById(@Param("id") Long id);
}
