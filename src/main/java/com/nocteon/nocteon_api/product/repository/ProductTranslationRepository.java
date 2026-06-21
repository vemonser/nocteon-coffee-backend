package com.nocteon.nocteon_api.product.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.product.entity.ProductTranslation;

public interface ProductTranslationRepository extends JpaRepository<ProductTranslation, Long> {
    Optional<ProductTranslation> findByProductIdAndLanguage(Long productId, String language);

    List<ProductTranslation> findByProductId(Long productId);
}