package com.nocteon.nocteon_api.product.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVariety;
import com.nocteon.nocteon_api.coffeeVariety.repository.CoffeeVarietyRepository;
import com.nocteon.nocteon_api.common.exception.notFound.CoffeeVarietyNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.ProcessingMethodNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.RoastLevelNotFoundException;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethod;
import com.nocteon.nocteon_api.processingMethod.repository.ProcessingMethodRepository;
import com.nocteon.nocteon_api.product.dto.request.CoffeeDetailsRequest;
import com.nocteon.nocteon_api.product.entity.CoffeeDetails;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.repository.CoffeeDetailsRepository;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevel;
import com.nocteon.nocteon_api.roastLevel.repository.RoastLevelRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductCoffeeDetailsService {

    private final CoffeeDetailsRepository coffeeDetailsRepository;
    private final RoastLevelRepository roastLevelRepository;
    private final ProcessingMethodRepository processingMethodRepository;
    private final CoffeeVarietyRepository coffeeVarietyRepository;

    @Transactional
    public void saveCoffeeDetails(Product product, CoffeeDetailsRequest cd) {
        RoastLevel roastLevel = null;
        if (cd.getRoastLevelSlug() != null) {
            roastLevel = roastLevelRepository
                    .findBySlug(cd.getRoastLevelSlug())
                    .orElseThrow(RoastLevelNotFoundException::new);
        }

        ProcessingMethod processingMethod = null;
        if (cd.getProcessingMethodSlug() != null) {
            processingMethod = processingMethodRepository
                    .findBySlug(cd.getProcessingMethodSlug())
                    .orElseThrow(ProcessingMethodNotFoundException::new);
        }

        CoffeeVariety coffeeVariety = null;
        if (cd.getCoffeeVarietySlug() != null) {
            coffeeVariety = coffeeVarietyRepository
                    .findBySlug(cd.getCoffeeVarietySlug())
                    .orElseThrow(CoffeeVarietyNotFoundException::new);
        }

        coffeeDetailsRepository.save(
                CoffeeDetails.builder()
                        .product(product)
                        .processingMethod(processingMethod)
                        .coffeeVariety(coffeeVariety)
                        .roastLevel(roastLevel)
                        .altitude(cd.getAltitude())
                        .harvestYear(cd.getHarvestYear())
                        .story(cd.getStory())
                        .build());
    }

    @Transactional
    public void replaceCoffeeDetails(Product product, ProductType productType, CoffeeDetailsRequest coffeeDetails) {
        coffeeDetailsRepository.deleteByProductId(product.getId());
        coffeeDetailsRepository.flush();
        if (productType == ProductType.COFFEE && coffeeDetails != null) {
            saveCoffeeDetails(product, coffeeDetails);
        }
    }
}
