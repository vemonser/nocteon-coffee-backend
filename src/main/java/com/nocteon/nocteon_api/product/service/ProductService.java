package com.nocteon.nocteon_api.product.service;

import java.util.List;
import java.util.Objects;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.exception.notFound.ProductNotFoundException;
import com.nocteon.nocteon_api.product.dto.request.ProductFilterRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductMediaRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductRequest;
import com.nocteon.nocteon_api.product.dto.response.DashboardProductResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductResponse;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.mapper.ProductResponseMapper;
import com.nocteon.nocteon_api.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductResponseMapper productResponseMapper;
    private final ProductMutationService productMutationService;

    public PageResponse<ProductCardResponse> getAll(ProductFilterRequest filter) {
        String language = LocaleContextHolder.getLocale().getLanguage();

        Page<Product> page = productRepository.findAllPublic(
                language,
                Objects.requireNonNullElse(filter.getSearch(), ""),
                filter.getCategorySlug(),
                filter.getOriginSlug(),
                filter.getProductType(),
                filter.getFeatured(),
                filter.toPageable());

        return PageResponse.of(page.map(p -> productResponseMapper.buildListResponse(p, language)));
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

        return productResponseMapper.buildDashboardResponse(product);
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
