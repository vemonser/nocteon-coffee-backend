package com.nocteon.nocteon_api.category.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.category.entity.CategoryTranslation;

public interface CategoryTranslationRepository extends JpaRepository<CategoryTranslation, Long> {
    Optional<CategoryTranslation> findByCategoryIdAndLanguage(Long categoryId, String language);

    List<CategoryTranslation> findByCategoryId(Long id);
    void deleteByCategoryId(Long categoryId);
}
