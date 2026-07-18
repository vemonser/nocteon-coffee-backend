package com.nocteon.nocteon_api.product.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethod;
import com.nocteon.nocteon_api.brewingMethod.repository.BrewingMethodRepository;
import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.category.repository.CategoryRepository;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidProductTypeException;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.notFound.BrewingMethodNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.CategoryNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.FarmNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.OriginNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.ProductNotFoundException;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.farm.entity.Farm;
import com.nocteon.nocteon_api.farm.repository.FarmRepository;
import com.nocteon.nocteon_api.origin.entity.Origin;
import com.nocteon.nocteon_api.origin.repository.OriginRepository;
import com.nocteon.nocteon_api.pairing.entity.Pairing;
import com.nocteon.nocteon_api.pairing.repository.PairingRepository;
import com.nocteon.nocteon_api.product.dto.request.ProductBrewingMethodRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductMediaRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductTranslationRequest;
import com.nocteon.nocteon_api.product.dto.response.DashboardProductResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductResponse;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductBrewingMethod;
import com.nocteon.nocteon_api.product.entity.ProductMedia;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.mapper.DashboardProductMapper;
import com.nocteon.nocteon_api.product.mapper.ProductResponseMapper;
import com.nocteon.nocteon_api.product.repository.ProductBrewingMethodRepository;
import com.nocteon.nocteon_api.product.repository.ProductMediaRepository;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.product.repository.ProductTranslationRepository;
import com.nocteon.nocteon_api.tastingNote.entity.TastingNote;
import com.nocteon.nocteon_api.tastingNote.repository.TastingNoteRepository;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductMutationService {

    private final ProductRepository productRepository;
    private final ProductTranslationRepository productTranslationRepository;
    private final ProductBrewingMethodRepository productBrewingMethodRepository;
    private final ProductMediaRepository productMediaRepository;
    private final CategoryRepository categoryRepository;
    private final OriginRepository originRepository;
    private final FarmRepository farmRepository;
    private final TastingNoteRepository tastingNoteRepository;
    private final PairingRepository pairingRepository;
    private final BrewingMethodRepository brewingMethodRepository;
    private final LookupServiceHelper helper;
    private final ProductMediaService productMediaService;
    private final ProductResponseMapper productResponseMapper;
    private final DashboardProductMapper dashboardProductMapper;
    private final ProductVariantService productVariantService;
    private final ProductCoffeeDetailsService productCoffeeDetailsService;

    @Transactional
    public ProductResponse create(ProductRequest request, List<MultipartFile> files) {
        helper.validateTranslations(request.getTranslations());
        productMediaService.validateMediaRequest(request.getMedia(), files);
        validateProductType(request);

        Category category = categoryRepository.findBySlug(request.getCategorySlug())
                .orElseThrow(CategoryNotFoundException::new);
        Origin origin = findOrigin(request.getOriginSlug());
        Farm farm = findFarm(request.getFarmSlug());

        String englishName = request.getTranslations().stream()
                .filter(t -> t.getLanguage().equals("en"))
                .findFirst()
                .map(ProductTranslationRequest::getName)
                .orElseThrow(InvalidTranslationException::new);

        String slug = helper.generateUniqueSlug(englishName, productRepository::existsBySlug);
        Product product = Product.builder()
                .slug(slug)
                .category(category)
                .origin(origin)
                .farm(farm)
                .productType(request.getProductType())
                .featured(request.isFeatured())
                .isActive(request.isActive())
                .build();
        product = productRepository.save(product);

        saveTranslations(product, request.getTranslations());
        productVariantService.saveVariants(product, request.getVariants());

        if (request.getProductType() == ProductType.COFFEE
                && request.getCoffeeDetails() != null) {
            productCoffeeDetailsService.saveCoffeeDetails(product, request.getCoffeeDetails());
        }

        attachTastingNotes(product, request.getTastingNoteSlugs());
        attachPairings(product, request.getPairingSlugs());
        saveBrewingMethods(product, request.getBrewingMethods());
        productMediaService.saveMedia(product, request.getMedia(), files);

        log.info("Product created with slug: {}", slug);
        return productResponseMapper.buildDetailResponse(
                productRepository.findBySlugAndLanguage(slug, LocaleContextHolder.getLocale().getLanguage())
                        .orElseThrow(ProductNotFoundException::new),
                LocaleContextHolder.getLocale().getLanguage());
    }

    @Transactional
    public DashboardProductResponse update(String slug, ProductRequest request, List<MultipartFile> files) {
        helper.validateTranslations(request.getTranslations());
        productMediaService.validateMediaRequest(request.getMedia(), files);
        validateProductType(request);

        Product product = productRepository.findBySlug(slug)
                .orElseThrow(ProductNotFoundException::new);

        product.setCategory(categoryRepository.findBySlug(request.getCategorySlug())
                .orElseThrow(CategoryNotFoundException::new));
        product.setOrigin(findOrigin(request.getOriginSlug()));
        product.setFarm(findFarm(request.getFarmSlug()));
        product.setProductType(request.getProductType());
        product.setFeatured(request.isFeatured());
        product.setActive(request.isActive());
        productRepository.save(product);

        upsertTranslations(product, request.getTranslations());
        productVariantService.replaceVariants(product, request.getVariants());
        productCoffeeDetailsService.replaceCoffeeDetails(product, request.getProductType(), request.getCoffeeDetails());
        replaceTastingNotes(product, request.getTastingNoteSlugs());
        replacePairings(product, request.getPairingSlugs());
        replaceBrewingMethods(product, request.getBrewingMethods());
        productMediaService.replaceMedia(product, request.getMedia(), files);

        productRepository.save(product);
        log.info("Product updated with slug: {}", slug);
        return dashboardProductMapper.buildDashboardResponse(product);
    }

    @Transactional
    public DashboardProductResponse toggleActive(String slug, boolean active) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(ProductNotFoundException::new);
        product.setActive(active);
        productRepository.save(product);
        return dashboardProductMapper.buildDashboardResponse(product);
    }

    @Transactional
    public DashboardProductResponse toggleFeatured(String slug, boolean featured) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(ProductNotFoundException::new);
        product.setFeatured(featured);
        productRepository.save(product);
        return dashboardProductMapper.buildDashboardResponse(product);
    }

    @Transactional
    public ProductResponse uploadMedia(
            String slug,
            List<ProductMediaRequest> mediaRequests,
            List<MultipartFile> files) {

        String language = LocaleContextHolder.getLocale().getLanguage();
        Product product = productRepository.findBySlugAndLanguage(slug, language)
                .orElseThrow(ProductNotFoundException::new);

        productMediaService.saveMedia(product, mediaRequests, files);

        return productResponseMapper.buildDetailResponse(product, language);
    }

    @Transactional
    public void deleteMedia(Long mediaId) {
        ProductMedia media = productMediaRepository.findById(mediaId)
                .orElseThrow(ProductNotFoundException::new);
        productMediaService.delete(media);
    }

    @Transactional
    public void delete(String slug) {
        Product product = productRepository.findBySlugAndLanguage(
                slug, LocaleContextHolder.getLocale().getLanguage())
                .orElseThrow(ProductNotFoundException::new);
        product.softDelete();
        productRepository.save(product);
        log.info("Product soft deleted: {}", slug);
    }

    private void validateProductType(ProductRequest request) {
        if (request.getProductType() == ProductType.EQUIPMENT
                && request.getCoffeeDetails() != null) {
            throw new InvalidProductTypeException();
        }
    }

    private Origin findOrigin(String slug) {
        if (slug == null) {
            return null;
        }
        return originRepository.findBySlug(slug)
                .orElseThrow(OriginNotFoundException::new);
    }

    private Farm findFarm(String slug) {
        if (slug == null) {
            return null;
        }
        return farmRepository.findBySlug(slug)
                .orElseThrow(FarmNotFoundException::new);
    }

    private void saveTranslations(Product product, List<ProductTranslationRequest> requests) {
        List<ProductTranslation> translations = new ArrayList<>();
        for (ProductTranslationRequest t : requests) {
            translations.add(ProductTranslation.builder()
                    .product(product)
                    .language(t.getLanguage())
                    .name(t.getName())
                    .shortDescription(t.getShortDescription())
                    .description(t.getDescription())
                    .build());
        }
        productTranslationRepository.saveAll(translations);
    }

    private void upsertTranslations(Product product, List<ProductTranslationRequest> requests) {
        for (ProductTranslationRequest t : requests) {
            productTranslationRepository.findByProductIdAndLanguage(product.getId(), t.getLanguage())
                    .ifPresentOrElse(existing -> {
                        existing.setName(t.getName());
                        existing.setShortDescription(t.getShortDescription());
                        existing.setDescription(t.getDescription());
                        productTranslationRepository.save(existing);
                    }, () -> productTranslationRepository.save(ProductTranslation.builder()
                            .product(product)
                            .language(t.getLanguage())
                            .name(t.getName())
                            .shortDescription(t.getShortDescription())
                            .description(t.getDescription())
                            .build()));
        }
    }

    private void attachTastingNotes(Product product, List<String> slugs) {
        if (slugs == null || slugs.isEmpty()) {
            return;
        }
        List<TastingNote> tastingNotes = tastingNoteRepository.findBySlugIn(slugs);
        product.getTastingNotes().addAll(tastingNotes);
        productRepository.save(product);
    }

    private void replaceTastingNotes(Product product, List<String> slugs) {
        product.getTastingNotes().clear();
        if (slugs != null && !slugs.isEmpty()) {
            product.getTastingNotes().addAll(tastingNoteRepository.findBySlugIn(slugs));
        }
    }

    private void attachPairings(Product product, List<String> slugs) {
        if (slugs == null || slugs.isEmpty()) {
            return;
        }
        List<Pairing> pairings = pairingRepository.findBySlugIn(slugs);
        product.getPairings().addAll(pairings);
        productRepository.save(product);
    }

    private void replacePairings(Product product, List<String> slugs) {
        product.getPairings().clear();
        if (slugs != null && !slugs.isEmpty()) {
            product.getPairings().addAll(pairingRepository.findBySlugIn(slugs));
        }
    }

    private void saveBrewingMethods(Product product, List<ProductBrewingMethodRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        for (ProductBrewingMethodRequest bm : requests) {
            productBrewingMethodRepository.save(buildBrewingMethod(product, bm));
        }
    }

    private void replaceBrewingMethods(Product product, List<ProductBrewingMethodRequest> requests) {
        productBrewingMethodRepository.deleteByProductId(product.getId());
        productBrewingMethodRepository.flush();
        saveBrewingMethods(product, requests);
    }

    private ProductBrewingMethod buildBrewingMethod(Product product, ProductBrewingMethodRequest bm) {
        BrewingMethod brewingMethod = brewingMethodRepository
                .findBySlug(bm.getBrewingMethodSlug())
                .orElseThrow(BrewingMethodNotFoundException::new);

        return ProductBrewingMethod.builder()
                .product(product)
                .brewingMethod(brewingMethod)
                .score(bm.getScore())
                .build();
    }
}
