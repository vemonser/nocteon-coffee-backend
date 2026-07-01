package com.nocteon.nocteon_api.product.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.cloudinary.service.CloudinaryService;
import com.nocteon.nocteon_api.common.exception.upload.ImageSizeExceededException;
import com.nocteon.nocteon_api.common.exception.upload.VideoSizeExceededException;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.product.dto.request.ProductMediaRequest;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductMedia;
import com.nocteon.nocteon_api.product.enums.MediaType;
import com.nocteon.nocteon_api.product.repository.ProductMediaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductMediaService {

    private final ProductMediaRepository productMediaRepository;
    private final LookupServiceHelper helper;
    private final CloudinaryService cloudinaryService;

    public void validateMediaRequest(
            List<ProductMediaRequest> mediaRequests,
            List<MultipartFile> files) {

        if (mediaRequests == null || mediaRequests.isEmpty()) {
            return;
        }

        if (files == null || files.size() != mediaRequests.size()) {
            throw new IllegalArgumentException(
                    "Uploaded files count must match media metadata count.");
        }

        long primaryCount = mediaRequests.stream()
                .filter(ProductMediaRequest::getIsPrimary)
                .count();

        if (primaryCount == 0) {
            throw new IllegalArgumentException(
                    "One media item must be primary.");
        }

        if (primaryCount > 1) {
            throw new IllegalArgumentException(
                    "Only one media item can be primary.");
        }
    }

    public void saveMedia(
            Product product,
            List<ProductMediaRequest> mediaRequests,
            List<MultipartFile> files) {

        if (mediaRequests == null || mediaRequests.isEmpty()) {
            return;
        }

        if (files == null || files.size() != mediaRequests.size()) {
            throw new IllegalArgumentException(
                    "Uploaded files count must match media metadata count.");
        }

        long primaryCount = mediaRequests.stream()
                .filter(ProductMediaRequest::getIsPrimary)
                .count();

        if (primaryCount > 1) {
            throw new IllegalArgumentException(
                    "Only one media item can be primary.");
        }

        int nextSortOrder = productMediaRepository
                .findByProductIdOrderBySortOrder(product.getId())
                .size();

        for (int i = 0; i < mediaRequests.size(); i++) {
            ProductMediaRequest mediaRequest = mediaRequests.get(i);
            MultipartFile file = files.get(i);

            if (Boolean.TRUE.equals(mediaRequest.getIsPrimary())) {
                productMediaRepository.findByProductIdAndIsPrimary(product.getId(), true)
                        .ifPresent(media -> {
                            media.setPrimary(false);
                            productMediaRepository.save(media);
                        });
            }

            validateMedia(file, mediaRequest.getType());

            String folder = mediaRequest.getType() == MediaType.IMAGE
                    ? "products/images"
                    : "products/videos";

            String url = helper.uploadMedia(
                    file,
                    folder,
                    mediaRequest.getType());

            productMediaRepository.save(
                    ProductMedia.builder()
                            .product(product)
                            .url(url)
                            .altText(mediaRequest.getAltText())
                            .type(mediaRequest.getType())
                            .sortOrder(nextSortOrder++)
                            .isPrimary(Boolean.TRUE.equals(mediaRequest.getIsPrimary()))
                            .build());
        }
    }

    public void replaceMedia(
            Product product,
            List<ProductMediaRequest> mediaRequests,
            List<MultipartFile> files) {

        if (mediaRequests == null || mediaRequests.isEmpty()) {
            return;
        }

        productMediaRepository.findByProductIdOrderBySortOrder(product.getId())
                .forEach(media -> cloudinaryService.delete(media.getUrl()));
        productMediaRepository.deleteByProductId(product.getId());

        saveMedia(product, mediaRequests, files);
    }

    public void delete(ProductMedia media) {
        cloudinaryService.delete(media.getUrl());
        productMediaRepository.delete(media);
    }

    private void validateMedia(MultipartFile file, MediaType type) {
        long size = file.getSize();

        switch (type) {
            case IMAGE -> {
                if (size > 5 * 1024 * 1024) {
                    throw new ImageSizeExceededException();
                }
            }
            case VIDEO -> {
                if (size > 100 * 1024 * 1024) {
                    throw new VideoSizeExceededException();
                }
            }
        }
    }
}
