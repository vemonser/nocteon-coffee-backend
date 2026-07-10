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

    long countByActiveTrue();

    Optional<PromoCode> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
