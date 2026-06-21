package com.nocteon.nocteon_api.product.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.product.entity.ProductMedia;

public interface ProductMediaRepository extends JpaRepository<ProductMedia, Long> {
    List<ProductMedia> findByProductIdOrderBySortOrder(Long productId);
    Optional<ProductMedia> findByProductIdAndIsPrimary(Long productId, boolean isPrimary);
    void deleteByProductId(Long productId);
}