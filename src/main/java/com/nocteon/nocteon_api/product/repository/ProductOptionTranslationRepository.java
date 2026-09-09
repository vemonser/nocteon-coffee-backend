package com.nocteon.nocteon_api.product.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.product.entity.ProductOptionTranslation;

public interface ProductOptionTranslationRepository extends JpaRepository<ProductOptionTranslation, Long> {

    Optional<ProductOptionTranslation> findByOptionIdAndLanguage(Long optionId, String language);
}