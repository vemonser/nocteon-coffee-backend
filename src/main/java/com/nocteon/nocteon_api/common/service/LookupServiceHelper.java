package com.nocteon.nocteon_api.common.service;

import java.util.List;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.cloudinary.service.CloudinaryService;
import com.nocteon.nocteon_api.common.dto.TranslationRequest;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.util.SlugUtils;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LookupServiceHelper {
    private final CloudinaryService cloudinaryService;

    public String generateUniqueSlug(String englishName, Predicate<String> existsCheck) {
        String baseSlug = SlugUtils.toSlug(englishName);
        if (!existsCheck.test(baseSlug))
            return baseSlug;

        int counter = 2;
        String candidate;
        do {
            candidate = baseSlug + "-" + counter++;
        } while (existsCheck.test(candidate));
        return candidate;
    }

    public String uploadImage(MultipartFile image, String folder) {
        if (image == null || image.isEmpty())
            return null;
        return cloudinaryService.uploadImage(image, folder);
    }

    public void deleteImageIfExists(String imageUrl) {
        if (imageUrl != null) {
            cloudinaryService.deleteImage(imageUrl);
        }
    }

    public void validateTranslations(List<? extends TranslationRequest> translations) {
        List<String> languages = translations.stream()
                .map(TranslationRequest::getLanguage)
                .toList();

        boolean hasAllRequired = List.of("en", "ar").stream()
                .allMatch(languages::contains);

        if (!hasAllRequired)
            throw new InvalidTranslationException();

        long distinct = translations.stream()
                .map(TranslationRequest::getLanguage)
                .distinct().count();

        if (distinct != translations.size())
            throw new InvalidTranslationException();
    }
}
