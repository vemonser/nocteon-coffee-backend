package com.nocteon.nocteon_api.common.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import java.util.Locale;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.cloudinary.service.CloudinaryService;
import com.nocteon.nocteon_api.common.dto.TranslationRequest;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.product.DuplicateTranslationException;
import com.nocteon.nocteon_api.common.util.SlugUtils;
import com.nocteon.nocteon_api.product.enums.MediaType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LookupServiceHelper {
    private final CloudinaryService cloudinaryService;

    @PersistenceContext
    private EntityManager entityManager;

    public String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        // Trim the same whitespace set that the SQL predicate trims (see
        // CategoryTranslationRepository.existsLiveTranslationName). Java's String.trim()
        // and PostgreSQL's TRIM()/\s disagree on tabs/newlines, so use an explicit,
        // identical character class in both places. Comparison-only: stored names are
        // never rewritten.
        return name.replaceAll("^[ \\t\\n\\r\\f\\v]+|[ \\t\\n\\r\\f\\v]+$", "")
                .toLowerCase(Locale.ROOT);
    }

    public void acquireNameLock(String entityType, String language, String normalizedName) {
        String key = "dupname:" + entityType + ":" + language + ":" + normalizedName;
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:k1), hashtext(:k2))")
                .setParameter("k1", key)
                .setParameter("k2", key + ":b")
                .getSingleResult();
    }

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

    public String uploadMedia( MultipartFile file, String folder,
            MediaType mediaType) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String resourceType = mediaType == MediaType.IMAGE
                ? "image"
                : "video";
        return cloudinaryService.upload(file, folder, resourceType);
    }
    public void deleteMediaIfExists(String imageUrl) {
        if (imageUrl != null) {
            cloudinaryService.delete(imageUrl);
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

    /**
     * Rejects a translation whose normalized name is already used by a live row of the same
     * entity type and language. Locks are taken in a stable order, matching
     * {@code CategoryService}, so concurrent creates cannot both pass the check.
     */
    public <T> void assertNoDuplicateNames(
            Collection<T> translations,
            Function<T, String> languageExtractor,
            Function<T, String> nameExtractor,
            String entityType,
            Long excludeId,
            NameConflictCheck conflictCheck) {
        translations.stream()
                .sorted(Comparator
                        .comparing(languageExtractor)
                        .thenComparing(t -> normalizeName(nameExtractor.apply(t))))
                .forEach(t -> {
                    String language = languageExtractor.apply(t);
                    String name = nameExtractor.apply(t);
                    acquireNameLock(entityType, language, normalizeName(name));
                    if (conflictCheck.exists(language, name, excludeId)) {
                        throw new DuplicateTranslationException();
                    }
                });
    }

    @FunctionalInterface
    public interface NameConflictCheck {
        boolean exists(String language, String name, Long excludeId);
    }
}
