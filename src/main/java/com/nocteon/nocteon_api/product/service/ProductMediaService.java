package com.nocteon.nocteon_api.product.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.cloudinary.service.CloudinaryService;
import com.nocteon.nocteon_api.common.exception.notFound.ProductMediaNotFoundException;
import com.nocteon.nocteon_api.common.exception.upload.ImageSizeExceededException;
import com.nocteon.nocteon_api.common.exception.upload.VideoSizeExceededException;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.product.dto.request.ProductMediaRequest;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductMedia;
import com.nocteon.nocteon_api.product.enums.MediaType;
import com.nocteon.nocteon_api.product.repository.ProductMediaRepository;

import jakarta.transaction.Transactional;
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

        long newItemsCount = mediaRequests.stream()
                .filter(m -> m.getId() == null)
                .count();

        if (files == null || files.size() != newItemsCount) {
            throw new IllegalArgumentException(
                    "Uploaded files count must match new media items count.");
        }

        long primaryCount = mediaRequests.stream()
                .filter(ProductMediaRequest::getIsPrimary)
                .count();

        if (primaryCount == 0) {
            throw new IllegalArgumentException("One media item must be primary.");
        }

        if (primaryCount > 1) {
            throw new IllegalArgumentException("Only one media item can be primary.");
        }
    }

    /**
     * Used only for CREATE (product has no existing media yet).
     * Every request item is necessarily new, so it maps 1:1 with files by index.
     */
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

        for (int i = 0; i < mediaRequests.size(); i++) {
            uploadAndSaveOne(product, mediaRequests.get(i), files.get(i), i);
        }
    }

    /**
     * Used for UPDATE. Reconciles the incoming list against what's already stored:
     * - items with an id present in the request -> kept, metadata updated in place
     * - items with an id NOT present in the request -> removed (Cloudinary + DB)
     * - items with id == null -> new upload, matched against `files` in order
     */
    @Transactional
    public void replaceMedia(
            Product product,
            List<ProductMediaRequest> mediaRequests,
            List<MultipartFile> files) {

        if (mediaRequests == null || mediaRequests.isEmpty()) {
            productMediaRepository.findByProductIdOrderBySortOrder(product.getId())
                    .forEach(media -> cloudinaryService.delete(media.getUrl()));
            productMediaRepository.deleteByProductId(product.getId());
            return;
        }

        List<ProductMedia> currentMedia = productMediaRepository
                .findByProductIdOrderBySortOrder(product.getId());

        Set<Long> incomingExistingIds = mediaRequests.stream()
                .map(ProductMediaRequest::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 1. Delete whatever existing media is NOT present in the incoming request
        currentMedia.stream()
                .filter(media -> !incomingExistingIds.contains(media.getId()))
                .forEach(media -> {
                    cloudinaryService.delete(media.getUrl());
                    productMediaRepository.delete(media);
                });

        Map<Long, ProductMedia> currentById = currentMedia.stream()
                .collect(Collectors.toMap(ProductMedia::getId, m -> m));

        int fileCursor = 0;

        for (int i = 0; i < mediaRequests.size(); i++) {
            ProductMediaRequest req = mediaRequests.get(i);

            if (req.getId() != null) {
                // 2. Existing item kept — update mutable fields only, no re-upload
                ProductMedia existing = currentById.get(req.getId());
                if (existing == null) {
                    throw new ProductMediaNotFoundException();
                }
                existing.setAltText(req.getAltText());
                existing.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : i);
                existing.setPrimary(Boolean.TRUE.equals(req.getIsPrimary()));
                productMediaRepository.save(existing);
            } else {
                // 3. New item — needs the next unused file
                if (files == null || fileCursor >= files.size()) {
                    throw new IllegalArgumentException(
                            "Missing uploaded file for new media item at index " + i);
                }
                uploadAndSaveOne(product, req, files.get(fileCursor), i);
                fileCursor++;
            }
        }
    }

    public void delete(ProductMedia media) {
        cloudinaryService.delete(media.getUrl());
        productMediaRepository.delete(media);
    }

    private void uploadAndSaveOne(
            Product product,
            ProductMediaRequest mediaRequest,
            MultipartFile file,
            int fallbackSortOrder) {

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

        String url = helper.uploadMedia(file, folder, mediaRequest.getType());

        productMediaRepository.save(
                ProductMedia.builder()
                        .product(product)
                        .url(url)
                        .altText(mediaRequest.getAltText())
                        .type(mediaRequest.getType())
                        .sortOrder(mediaRequest.getSortOrder() != null
                                ? mediaRequest.getSortOrder()
                                : fallbackSortOrder)
                        .isPrimary(Boolean.TRUE.equals(mediaRequest.getIsPrimary()))
                        .build());
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