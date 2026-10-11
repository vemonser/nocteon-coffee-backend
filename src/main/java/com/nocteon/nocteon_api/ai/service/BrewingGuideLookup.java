package com.nocteon.nocteon_api.ai.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethod;
import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethodTranslation;
import com.nocteon.nocteon_api.brewingMethod.repository.BrewingMethodRepository;

import lombok.RequiredArgsConstructor;

/**
 * The brewing guides the catalog stores, as the strings the model is given.
 *
 * <h2>Why the stored text is returned verbatim</h2>
 * A brewing method is the one thing a small model will answer fluently and wrongly. It has read
 * thousands of V60 recipes and will produce a plausible one for a method this shop has never sold
 * — so the guide is returned exactly as written, and the tool's own description tells the model to
 * repeat it rather than restate it.
 *
 * <h2>Why an unknown method answers with an instruction</h2>
 * The same convention as the product tools: say what happened, say what the model must do about
 * it, and say what it must not do. "No guide is stored" is a fact the model can obey; an empty
 * string is an invitation to write one from memory.
 *
 * <h2>Why this throws and {@code get_brewing_guide} does not</h2>
 * The never-throw contract belongs to the tool, not to the read. A failure here has to become the
 * "list the stored methods" instruction, and only the tool holds that instruction.
 */
@Component
@RequiredArgsConstructor
public class BrewingGuideLookup {

    /**
     * The instruction returned when no guide is stored for the requested method.
     *
     * <p>Package-private rather than private so tests can assert the tool returns <em>this</em>
     * value rather than merely something non-blank — the wording is the contract, not an
     * implementation detail.
     */
    static final String UNKNOWN_BREWING_METHOD =
            "No brewing guide is stored for that method. List the stored methods instead of "
                    + "describing one from memory.";

    private final BrewingMethodRepository brewingMethodRepository;

    /**
     * The stored description for one method slug.
     *
     * <p>No transaction is opened here, unlike the product fact sheet: this repository method
     * join-fetches the translations it returns, so there is nothing left to lazy-load.
     *
     * @param method the brewing method slug, already trimmed and lower-cased
     * @return the stored description, or {@link #UNKNOWN_BREWING_METHOD}
     */
    public String guideFor(String method) {
        Optional<BrewingMethod> found = brewingMethodRepository.findBySlugWithTranslations(method);
        if (found.isEmpty()) {
            return UNKNOWN_BREWING_METHOD;
        }

        String description = descriptionFor(found.get(), TranslatedNames.currentLanguage());
        return StringUtils.hasText(description) ? description.trim() : UNKNOWN_BREWING_METHOD;
    }

    /**
     * The description in the request language, then whatever is stored.
     *
     * <p>The last step is any translation rather than English specifically: a guide that exists in
     * Arabic but not in the request language is still better handed over for the model to
     * translate than withheld, because the point is that it is a <em>house</em> description.
     */
    private String descriptionFor(BrewingMethod method, String language) {
        List<BrewingMethodTranslation> translations = method.getTranslations();
        if (translations == null) {
            return null;
        }

        String described = TranslatedNames.pick(
                translations, language, BrewingMethodTranslation::getDescription);
        if (StringUtils.hasText(described)) {
            return described;
        }
        return translations.stream()
                .map(BrewingMethodTranslation::getDescription)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    /**
     * The slug the model passed, normalised.
     *
     * <p>Lower-cased because the slug is the only identifier the model has, and a visitor who asks
     * about "French-Press" should get the same guide as one who typed "french-press".
     */
    static String normalise(String method) {
        return method.trim().toLowerCase(Locale.ROOT);
    }
}