package com.nocteon.nocteon_api.product.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.product.entity.ProductOptionValueTranslation;

public interface ProductOptionValueTranslationRepository extends JpaRepository<ProductOptionValueTranslation, Long> {

    Optional<ProductOptionValueTranslation> findByOptionValueIdAndLanguage(Long optionValueId, String language);
}