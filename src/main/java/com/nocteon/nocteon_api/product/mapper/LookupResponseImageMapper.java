package com.nocteon.nocteon_api.product.mapper;

import java.util.List;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.product.dto.response.summary.LockupResponseImage;

@Component
public class LookupResponseImageMapper {

        public <T> LockupResponseImage buildLookupImage(
                        String slug,
                        List<T> translations,
                        String language,
                        String imageUrl,
                        Function<T, String> languageExtractor,
                        Function<T, String> nameExtractor,
                        Function<T, String> descriptionExtractor) {
                if (slug == null) {
                        return null;
                }

                String name = translations.stream()
                                .filter(t -> languageExtractor.apply(t).equals(language))
                                .findFirst()
                                .map(nameExtractor)
                                .orElse(slug);

                String description = translations.stream()
                                .filter(t -> languageExtractor.apply(t).equals(language))
                                .findFirst()
                                .map(descriptionExtractor)
                                .orElse(null);

                return LockupResponseImage.builder()
                                .slug(slug)
                                .name(name)
                                .description(description)
                                .imageUrl(imageUrl)
                                .build();
        }
}
