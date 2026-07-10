package com.nocteon.nocteon_api.product.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.product.entity.CoffeeDetails;

public interface CoffeeDetailsRepository extends JpaRepository<CoffeeDetails, Long> {

    Optional<CoffeeDetails> findByProductId(Long productId);

    @Modifying
    @Query("""
        delete
        from CoffeeDetails cd
        where cd.product.id = :productId
    """)
    void deleteByProductId(@Param("productId") Long productId);
}