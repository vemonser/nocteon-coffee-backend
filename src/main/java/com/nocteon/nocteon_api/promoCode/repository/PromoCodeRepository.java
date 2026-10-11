package com.nocteon.nocteon_api.promoCode.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.promoCode.entity.PromoCode;

public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {

    @Query("""
                SELECT pc FROM PromoCode pc
                WHERE LOWER(pc.code) LIKE LOWER(CONCAT('%', :query, '%'))
            """)
    List<PromoCode> searchPromoCodes(@Param("query") String query, Pageable pageable);

        /**
         * How many rows the search query matches, uncapped.
         *
         * <p>The search takes a fixed-size page of rows, so the returned list cannot be used
         * to report a total: it is the page size, not the match count. This mirrors the row
         * query's predicates exactly so the two cannot drift apart.
         */
        @Query("""
                        SELECT COUNT(pc) FROM PromoCode pc WHERE LOWER(pc.code) LIKE LOWER(CONCAT('%', :query, '%'))
                        """)
        long countSearchPromoCodes(@Param("query") String query);

    long countByActiveTrue();

    Optional<PromoCode> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
