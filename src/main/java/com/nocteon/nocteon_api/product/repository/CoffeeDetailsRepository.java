package com.nocteon.nocteon_api.product.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.product.entity.CoffeeDetails;

public interface CoffeeDetailsRepository extends JpaRepository<CoffeeDetails, Long> {

    Optional<CoffeeDetails> findByProductId(Long productId);

    @Query("SELECT COUNT(cd) FROM CoffeeDetails cd WHERE cd.roastLevel.id = :roastLevelId")
    long countByRoastLevelId(@Param("roastLevelId") Long roastLevelId);

    @Query("SELECT COUNT(cd) FROM CoffeeDetails cd WHERE cd.processingMethod.id = :processingMethodId")
    long countByProcessingMethodId(@Param("processingMethodId") Long processingMethodId);

    @Query("SELECT COUNT(cd) FROM CoffeeDetails cd WHERE cd.coffeeVariety.id = :coffeeVarietyId")
    long countByCoffeeVarietyId(@Param("coffeeVarietyId") Long coffeeVarietyId);

    @Modifying
    @Query("""
        delete
        from CoffeeDetails cd
        where cd.product.id = :productId
    """)
    void deleteByProductId(@Param("productId") Long productId);
}