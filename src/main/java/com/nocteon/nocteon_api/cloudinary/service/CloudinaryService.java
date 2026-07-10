package com.nocteon.nocteon_api.cloudinary.service;

import java.io.IOException;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.nocteon.nocteon_api.common.exception.image.ImageUploadException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {
    private final Cloudinary cloudinary;

    public String upload(MultipartFile file, String folder, String resourceType) {
        try {
            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "nocteon/" + folder,
                            "resource_type", resourceType,
                            "transformation", "q_auto,f_auto"));

            return (String) result.get("secure_url");

        } catch (IOException e) {
            log.error("Failed to upload media to Cloudinary", e);
            throw new ImageUploadException();
        }
    }

    public void delete(String mediaUrl) {
        String publicId = extractPublicId(mediaUrl);

        if (publicId == null) {
            log.warn("Skipping Cloudinary delete for non-Cloudinary URL: {}", mediaUrl);
            return;
        }

        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (IOException e) {
            log.error("Failed to delete media", e);
            throw new ImageUploadException();
        }
    }

    private String extractPublicId(String imageUrl) {
        if (imageUrl == null || !imageUrl.contains("nocteon/")) {
            return null;
        }

        int dotIndex = imageUrl.lastIndexOf('.');
        if (dotIndex == -1) {
            return null;
        }

        String withoutExtension = imageUrl.substring(0, dotIndex);
        int nocteonIndex = withoutExtension.indexOf("nocteon/");

        return nocteonIndex == -1 ? null : withoutExtension.substring(nocteonIndex);
    }
}