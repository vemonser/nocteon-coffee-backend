package com.nocteon.nocteon_api.ai.service;

import java.util.List;
import java.util.function.Function;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.util.StringUtils;

import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethodTranslation;
import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVarietyTranslation;
import com.nocteon.nocteon_api.farm.entity.FarmTranslation;
import com.nocteon.nocteon_api.origin.entity.OriginTranslation;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethodTranslation;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevelTranslation;
import com.nocteon.nocteon_api.tastingNote.entity.TastingNoteTranslation;

/**
 * Reading one translated field of one translatable row.
 *
 * <h2>Why there is no shared interface</h2>
 * Every translatable entity in this schema declares its own {@code translations} collection with
 * its own element type, and unifying them would mean either a mapped supertype spanning nine
 * unrelated tables or a reflective accessor. The switch in {@link #languageOf(Object)} is the cheap
 * version of both: one place that knows which language column each row type has, and no change to
 * any entity.
 *
 * <h2>Why the fallback order is request language first</h2>
 * These strings are the assistant's evidence, and the assistant rewrites them into whatever
 * language the visitor is speaking. English is second because it is the only translation
 * guaranteed to exist. There is deliberately no slug fallback here: a slug inside a fact sheet
 * reads to a small model as a fact, which is the one thing it must not be.
 *
 * <p>Shared by the fact sheet and the brewing-guide lookup because both answer the same question
 * about the same rows — "what is this called, in this language" — and duplicating the ordering
 * rule in two places would let the two drift apart.
 */
final class TranslatedNames {

    /** The only translation every seeded row is guaranteed to carry. */
    static final String ENGLISH = "en";

    private TranslatedNames() {
    }

    /**
     * The name in the request language, then in English.
     *
     * @param translations the entity's translations, or {@code null}
     * @param language     the language to prefer
     * @param nameOf       the field holding the display name
     * @return the translated name, or {@code null} when no translation carries one
     */
    static <T> String nameIn(List<T> translations, String language, Function<T, String> nameOf) {
        String match = pick(translations, language, nameOf);
        return StringUtils.hasText(match) ? match : pick(translations, ENGLISH, nameOf);
    }

    /**
     * The first non-blank value stored for one language.
     *
     * <p>{@code findFirst} over a {@link List} rather than a map lookup because these collections
     * are {@code @OneToMany} bags with no uniqueness guarantee on the language column in the
     * schema; a duplicate row must not become a {@code null} key or an exception.
     *
     * @return the value, or {@code null} when the language is absent or blank
     */
    static <T> String pick(List<T> translations, String language, Function<T, String> nameOf) {
        if (translations == null) {
            return null;
        }
        return translations.stream()
                .filter(translation -> languageOf(translation).equals(language))
                .map(nameOf)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    /** The language column of any translatable row, without a shared interface to depend on. */
    private static String languageOf(Object translation) {
        return switch (translation) {
            case ProductTranslation product -> product.getLanguage();
            case OriginTranslation origin -> origin.getLanguage();
            case FarmTranslation farm -> farm.getLanguage();
            case RoastLevelTranslation roast -> roast.getLanguage();
            case ProcessingMethodTranslation process -> process.getLanguage();
            case CoffeeVarietyTranslation variety -> variety.getLanguage();
            case TastingNoteTranslation note -> note.getLanguage();
            case BrewingMethodTranslation brewing -> brewing.getLanguage();
            default -> "";
        };
    }

    /** The language the current request is being served in, for fact assembly. */
    static String currentLanguage() {
        return LocaleContextHolder.getLocale().getLanguage();
    }
}