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

    public String uploadImage(MultipartFile file, String folder) {
        try {
            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "nocteon/" + folder,
                            "resource_type", "image",
                            "transformation", "q_auto,f_auto"));

            return (String) result.get("secure_url");

        } catch (IOException e) {
            log.error("Failed to upload image to Cloudinary", e);
            throw new ImageUploadException();
        }
    }

    public void deleteImage(String imageUrl) {
        try {
            String publicId = extractPublicId(imageUrl);
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (IOException e) {
            log.error("Failed to delete image from Cloudinary", e);
            throw new ImageUploadException();
        }
    }

    private String extractPublicId(String imageUrl) {
        String withoutExtension = imageUrl.substring(0, imageUrl.lastIndexOf('.'));
        return withoutExtension.substring(withoutExtension.indexOf("nocteon/"));
    }
}
