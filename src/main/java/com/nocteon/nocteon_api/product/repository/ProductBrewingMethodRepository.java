package com.nocteon.nocteon_api.product.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.product.entity.ProductBrewingMethod;

public interface ProductBrewingMethodRepository extends JpaRepository<ProductBrewingMethod, Long> {
    List<ProductBrewingMethod> findByProductId(Long productId);
    void deleteByProductId(Long productId);
}