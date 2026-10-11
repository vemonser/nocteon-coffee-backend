package com.nocteon.nocteon_api.product.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.ai.service.SemanticProductSearch;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.exception.notFound.ProductNotFoundException;
import com.nocteon.nocteon_api.common.util.ProductSortResolver;
import com.nocteon.nocteon_api.product.dto.request.ProductFilterRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductMediaRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductRequest;
import com.nocteon.nocteon_api.product.dto.response.DashboardProductResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductOptionOptionResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductOptionValueOptionResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductResponse;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductOption;
import com.nocteon.nocteon_api.product.entity.ProductOptionTranslation;
import com.nocteon.nocteon_api.product.entity.ProductOptionValue;
import com.nocteon.nocteon_api.product.entity.ProductOptionValueTranslation;
import com.nocteon.nocteon_api.product.mapper.DashboardProductMapper;
import com.nocteon.nocteon_api.product.mapper.ProductResponseMapper;
import com.nocteon.nocteon_api.product.repository.ProductOptionValueRepository;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;
import com.nocteon.nocteon_api.product.repository.specification.ProductSpecifications;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductResponseMapper productResponseMapper;
    private final DashboardProductMapper dashboardProductMapper;
    private final ProductMutationService productMutationService;
    private final ProductOptionValueRepository productOptionValueRepository;
    private final SemanticProductSearch semanticProductSearch;

    /**
     * The public catalog listing. Answers with vector-similarity ranking when the caller opts
     * in via {@code ?semantic=true} with real search text, and with the existing keyword query
     * otherwise — including whenever the semantic path cannot answer.
     *
     * <p>The semantic branch is {@code SemanticProductSearch.rank}, which returns
     * {@code Optional.empty()} rather than an empty page when the vector signal is unusable, so
     * an unreachable store or an all-filtered-out candidate set degrades to the plain listing
     * instead of claiming the catalog has nothing. {@code Optional.orElseGet} with
     * {@link #keywordSearch} is what performs that fallback, and it is deliberately lazy: the
     * keyword query is not issued on the semantic path.
     *
     * @return the requested page of cards; the response shape is identical either way, only
     *         the ordering and the page membership differ
     */
    public PageResponse<ProductCardResponse> getAll(ProductFilterRequest filter) {
        return semanticProductSearch.rank(filter)
                .orElseGet(() -> keywordSearch(filter));
    }

    private PageResponse<ProductCardResponse> keywordSearch(ProductFilterRequest filter) {
        String language = LocaleContextHolder.getLocale().getLanguage();

        String sort = switch (filter.getSort()) {
            case "name", "price", "createdAt", "featured" -> filter.getSort();
            default -> "createdAt";
        };

        filter.setSort(sort);

        Specification<Product> spec = ProductSpecifications.withFilters(filter, language,
                countDistinctOptions(filter));
        Sort resolvedSort = ProductSortResolver.resolve(sort, filter.getDirection());
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), resolvedSort);

        Page<Product> page = productRepository.findAll(spec, pageable);

        return PageResponse.of(
                page.map(p -> productResponseMapper.buildListResponse(p, language)));
    }

    /**
     * How many distinct options the requested option values span, which
     * {@link ProductSpecifications} needs to require <em>all</em> of them. Zero when no option
     * filter was asked for.
     *
     * <p>Kept here rather than shared with {@code SemanticProductSearch}, which has its own copy:
     * the two paths build different specifications for different purposes, and the value is four
     * lines of a single conditional.
     */
    private long countDistinctOptions(ProductFilterRequest filter) {
        if (filter.getOptionValueIds() == null || filter.getOptionValueIds().isEmpty()) {
            return 0;
        }
        return productOptionValueRepository
                .countDistinctOptionsByValueIds(filter.getOptionValueIds());
    }

    // في ProductService
    public List<ProductOptionOptionResponse> getAvailableOptions(ProductFilterRequest filter) {
        String language = LocaleContextHolder.getLocale().getLanguage();

        ProductFilterRequest filterWithoutOptions = copyWithoutOptionValueIds(filter);

        Specification<Product> spec = ProductSpecifications.withFilters(filterWithoutOptions, language, 0);
        List<Long> productIds = productRepository.findAll(spec).stream()
                .map(Product::getId)
                .distinct()
                .toList();

        if (productIds.isEmpty()) {
            return List.of();
        }

        List<ProductOptionValue> optionValues = productVariantRepository
                .findDistinctOptionValuesByProductIds(productIds);

        optionValues.sort(
                Comparator.comparing((ProductOptionValue ov) -> ov.getOption().getSortOrder())
                        .thenComparing(ProductOptionValue::getSortOrder));

        Map<Long, String> optionNames = new LinkedHashMap<>();
        Map<Long, List<ProductOptionValueOptionResponse>> valuesByOption = new LinkedHashMap<>();

        for (ProductOptionValue ov : optionValues) {
            ProductOption option = ov.getOption();

            optionNames.computeIfAbsent(option.getId(), id -> option.getTranslations().stream()
                    .filter(t -> t.getLanguage().equals(language))
                    .findFirst()
                    .map(ProductOptionTranslation::getName)
                    .orElse(option.getSlug()));

            String value = ov.getTranslations().stream()
                    .filter(t -> t.getLanguage().equals(language))
                    .findFirst()
                    .map(ProductOptionValueTranslation::getValue)
                    .orElse(ov.getSlug());

            valuesByOption
                    .computeIfAbsent(option.getId(), id -> new ArrayList<>())
                    .add(ProductOptionValueOptionResponse.builder()
                            .id(ov.getId())
                            .slug(ov.getSlug())
                            .value(value)
                            .build());
        }

        return optionNames.entrySet().stream()
                .map(entry -> {
                    Long optionId = entry.getKey();
                    // نجيب slug الأوبشن من أول value بتاعتها (كلهم بنفس option فمتساوين)
                    String optionSlug = optionValues.stream()
                            .filter(ov -> ov.getOption().getId().equals(optionId))
                            .findFirst()
                            .map(ov -> ov.getOption().getSlug())
                            .orElse("");

                    return ProductOptionOptionResponse.builder()
                            .id(optionId)
                            .slug(optionSlug)
                            .name(entry.getValue())
                            .values(valuesByOption.get(optionId))
                            .build();
                })
                .toList();
    }

    private ProductFilterRequest copyWithoutOptionValueIds(ProductFilterRequest filter) {
        ProductFilterRequest copy = new ProductFilterRequest();
        copy.setSearch(filter.getSearch());
        copy.setCategorySlug(filter.getCategorySlug());
        copy.setOriginSlug(filter.getOriginSlug());
        copy.setFarmSlug(filter.getFarmSlug());
        copy.setMinRating(filter.getMinRating());
        copy.setProductType(filter.getProductType());
        copy.setFeatured(filter.getFeatured());
        copy.setIsActive(filter.getIsActive());
        copy.setMinPrice(filter.getMinPrice());
        copy.setMaxPrice(filter.getMaxPrice());
        copy.setBrewingMethodSlug(filter.getBrewingMethodSlug());
        copy.setInStockOnly(filter.getInStockOnly());
        copy.setOnSaleOnly(filter.getOnSaleOnly());
        return copy;
    }

    public PageResponse<ProductCardResponse> getAllDashboard(ProductFilterRequest filter) {
        String language = LocaleContextHolder.getLocale().getLanguage();

        Page<Product> page = findDashboardPage(filter);

        return PageResponse.of(
                page.map(p -> productResponseMapper.buildListResponse(p, language)));
    }

    public DashboardProductResponse getDashboardBySlug(String slug) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(ProductNotFoundException::new);

        return dashboardProductMapper.buildDashboardResponse(product);
    }

    public ProductResponse getBySlug(String slug) {
        String language = LocaleContextHolder.getLocale().getLanguage();

        Product product = productRepository.findBySlugAndLanguage(slug, language)
                .orElseThrow(ProductNotFoundException::new);

        return productResponseMapper.buildDetailResponse(product, language);
    }

    public ProductResponse create(ProductRequest request, List<MultipartFile> files) {
        return productMutationService.create(request, files);
    }

    public DashboardProductResponse update(String slug, ProductRequest request, List<MultipartFile> files) {
        return productMutationService.update(slug, request, files);
    }

    public DashboardProductResponse toggleActive(String slug, boolean active) {
        return productMutationService.toggleActive(slug, active);
    }

    public DashboardProductResponse toggleFeatured(String slug, boolean featured) {
        return productMutationService.toggleFeatured(slug, featured);
    }

    public ProductResponse uploadMedia(
            String slug,
            List<ProductMediaRequest> mediaRequests,
            List<MultipartFile> files) {

        return productMutationService.uploadMedia(slug, mediaRequests, files);
    }

    public void deleteMedia(Long mediaId) {
        productMutationService.deleteMedia(mediaId);
    }

    public void delete(String slug) {
        productMutationService.delete(slug);
    }

    private Page<Product> findDashboardPage(ProductFilterRequest filter) {
        return productRepository.findAllDashboard(
                Objects.requireNonNullElse(filter.getSearch(), ""),
                filter.getCategorySlug(),
                filter.getProductType(),
                filter.getIsActive(),
                filter.getFeatured(),
                filter.toPageable());
    }
}
