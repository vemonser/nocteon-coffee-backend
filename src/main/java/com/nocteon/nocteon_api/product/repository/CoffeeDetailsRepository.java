package com.nocteon.nocteon_api.product.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.product.entity.CoffeeDetails;

public interface CoffeeDetailsRepository extends JpaRepository<CoffeeDetails, Long> {
    Optional<CoffeeDetails> findByProductId(Long productId);
}