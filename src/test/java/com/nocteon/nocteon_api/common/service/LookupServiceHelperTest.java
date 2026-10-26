package com.nocteon.nocteon_api.common.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.nocteon.nocteon_api.common.dto.TranslationRequest;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;

class LookupServiceHelperTest {

    private final LookupServiceHelper helper = new LookupServiceHelper(null);

    private TranslationRequest translation(String language, String name) {
        return new TranslationRequest() {
            @Override
            public String getLanguage() {
                return language;
            }

            @Override
            public String getName() {
                return name;
            }
        };
    }

    private List<TranslationRequest> validPair() {
        return List.of(translation("en", "Ethiopia"), translation("ar", "إثيوبيا"));
    }

    @Test
    void rejectsNullName() {
        List<TranslationRequest> translations = List.of(
                translation("en", null), translation("ar", "إثيوبيا"));

        assertThatThrownBy(() -> helper.validateTranslations(translations))
                .isInstanceOf(InvalidTranslationException.class);
    }

    @Test
    void rejectsEmptyName() {
        List<TranslationRequest> translations = List.of(
                translation("en", ""), translation("ar", "إثيوبيا"));

        assertThatThrownBy(() -> helper.validateTranslations(translations))
                .isInstanceOf(InvalidTranslationException.class);
    }

    @Test
    void rejectsWhitespaceOnlyName() {
        for (String blank : List.of(" ", "   ", "\t", "\n", " \t\r\n ")) {
            List<TranslationRequest> translations = List.of(
                    translation("en", blank), translation("ar", "إثيوبيا"));

            assertThatThrownBy(() -> helper.validateTranslations(translations))
                    .as("blank name %s", blank)
                    .isInstanceOf(InvalidTranslationException.class);
        }
    }

    @Test
    void acceptsValidTranslations() {
        assertThatCode(() -> helper.validateTranslations(validPair()))
                .doesNotThrowAnyException();
    }

    @Test
    void stillRejectsMissingLanguage() {
        List<TranslationRequest> translations = List.of(translation("en", "Ethiopia"));

        assertThatThrownBy(() -> helper.validateTranslations(translations))
                .isInstanceOf(InvalidTranslationException.class);
    }
}
