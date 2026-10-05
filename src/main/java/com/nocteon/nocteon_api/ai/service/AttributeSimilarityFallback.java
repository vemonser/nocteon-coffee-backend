package com.nocteon.nocteon_api.ai.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.category.entity.CategoryTranslation;
import com.nocteon.nocteon_api.origin.entity.Origin;
import com.nocteon.nocteon_api.origin.entity.OriginTranslation;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethod;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethodTranslation;
import com.nocteon.nocteon_api.product.entity.CoffeeDetails;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevel;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevelTranslation;
import com.nocteon.nocteon_api.tastingNote.entity.TastingNote;
import com.nocteon.nocteon_api.tastingNote.entity.TastingNoteTranslation;

import lombok.RequiredArgsConstructor;

/**
 * Scores catalog products by how many tasting attributes they share with a source product.
 *
 * <p>The safety net under the vector path. When Ollama is unreachable, the feature flag is off,
 * or nothing has been indexed, {@link SimilarProductService} still has to answer "what else
 * should I look at" — and plain attribute overlap answers that well enough for coffee, where
 * origin, roast, process and notes genuinely drive substitution. It is not a ranking model and
 * does not pretend to be one.
 *
 * <h2>Why it compares slugs and not entities</h2>
 * <b>Ranking</b> compares relations by {@code slug}, never by object identity, id, or display
 * name. JPA hands out a different instance per persistence context, so identity comparison would
 * score every candidate at zero; ids cannot be used to compare relations because they are null
 * for anything built outside a session; and a display name is translatable, so two distinct
 * origins could compare equal in one locale and not another. Slugs are stable, ASCII and
 * locale-independent, which also lets the same comparison work on detached instances loaded by
 * two different queries.
 *
 * <p>Display names appear only in {@link #reason}, never in a weight.
 *
 * <h2>Why the reason text is localized, and how</h2>
 * A reason chip sits directly under a product name that <em>is</em> translated, so an English
 * chip under an Arabic card reads as a bug to the customer — and a slug ({@code ethiopia})
 * under an Arabic label is wrong twice over. So the labels come from {@code messages} /
 * {@code messages_ar} via {@link MessageSource}, and the attribute values are the relations'
 * <em>translated names</em>, resolved for the request locale exactly as the rest of the
 * codebase resolves them (see {@code GlobalSearchService}): the matching language, else any
 * translation, else the slug.
 *
 * <p>The slug is the last resort rather than the first because it is the one identifier
 * guaranteed to exist. An untranslated chip is a defect; a blank one renders as an empty
 * element that looks like a rendering failure.
 *
 * <p>{@link MessageSource} is the only collaborator. It brings no I/O of its own — the bundles
 * are cached after first read — so the class stays constructible in a plain unit test with no
 * Spring context, which is what keeps the net independently testable: these tests still pass
 * when the entire AI path is broken, which is exactly the moment they matter.
 *
 * <h2>The weights</h2>
 * Origin {@code +0.4}, roast level {@code +0.2}, each shared tasting note {@code +0.15} capped
 * in total at {@code +0.3}, processing method {@code +0.1}, category {@code +0.1}.
 *
 * <p>Origin dominates because it is the attribute that most reliably predicts whether a second
 * bag of coffee will be liked by someone who liked the first — a washed Ethiopian and a washed
 * Colombian are both answers to the same craving, while sharing a category does not make one a
 * substitute for the other. Tasting notes are capped rather than summed freely because
 * otherwise a candidate listing five common notes would outrank an exact-origin match, and note
 * overlap is the least discriminating signal of the five: nearly every specialty coffee has
 * something berry in it.
 */
@Component
@RequiredArgsConstructor
public class AttributeSimilarityFallback {

    private static final double ORIGIN_WEIGHT = 0.4;
    private static final double ROAST_LEVEL_WEIGHT = 0.2;
    private static final double TASTING_NOTE_WEIGHT = 0.15;

    /**
     * Ceiling on the whole tasting-note contribution.
     *
     * <p>Without it, note overlap alone could reach 0.45 and outrank a shared origin at 0.4.
     */
    private static final double MAX_TASTING_NOTE_SCORE = 0.3;

    private static final double PROCESSING_METHOD_WEIGHT = 0.1;
    private static final double CATEGORY_WEIGHT = 0.1;

    private static final String NEUTRAL_REASON_KEY = "ai.similar.reason.neutral";
    private static final String ORIGIN_LABEL_KEY = "ai.similar.reason.origin";
    private static final String ROAST_LABEL_KEY = "ai.similar.reason.roast";
    private static final String NOTES_LABEL_KEY = "ai.similar.reason.notes";
    private static final String PROCESS_LABEL_KEY = "ai.similar.reason.process";
    private static final String CATEGORY_LABEL_KEY = "ai.similar.reason.category";

    private static final String REASON_SEPARATOR = " · ";

    /** Joins the note names inside the "Notes:" label. */
    private static final String NOTE_SEPARATOR = ", ";

    private final MessageSource messageSource;

    /**
     * Scores every candidate against the source.
     *
     * <p>The source is excluded by id — a "similar products" rail that leads with the product the
     * customer is already looking at is worse than one that shows nothing.
     *
     * <p>Insertion order is the candidate order, and the scores are returned raw and unclamped:
     * {@code 0.0} is a legitimate result meaning "nothing shared", and the caller ranks and
     * truncates.
     *
     * @param source      the product being viewed; never {@code null}
     * @param candidates  the products to score, in any order; entries may be {@code null}
     * @return product id to attribute score, excluding the source and any entry without an id
     */
    public Map<Long, Double> rank(Product source, List<Product> candidates) {
        Map<Long, Double> scores = new LinkedHashMap<>();

        for (Product candidate : candidates) {
            if (candidate == null || candidate.getId() == null || isSameProduct(source, candidate)) {
                continue;
            }
            scores.put(candidate.getId(), score(source, candidate));
        }

        return scores;
    }

    /**
     * Describes why a candidate was offered, in the customer's language.
     *
     * <p>Always returns something renderable. The response contract promises a non-blank
     * {@code reason} on every item, and an empty string would surface as a blank chip that looks
     * like a rendering bug — so a zero score, an unresolvable match set, or a pair sharing
     * nothing all collapse to a localized neutral phrase rather than to {@code ""}.
     *
     * @param source      the product being viewed; never {@code null}
     * @param candidate   the product being offered; never {@code null}
     * @param score       the score the pair was given; decides only whether an explanation is
     *                    owed at all
     * @param language    the requested language code; null or blank resolves to the default
     *                    bundle rather than throwing
     * @return a non-blank reason, localized and using translated attribute names
     */
    public String reason(Product source, Product candidate, double score, String language) {
        Locale locale = localeFor(language);

        if (score <= 0.0) {
            return neutralReason(language);
        }

        List<String> labels = matchedLabels(source, candidate, locale);

        return labels.isEmpty() ? neutralReason(language) : String.join(REASON_SEPARATOR, labels);
    }

    /**
     * The neutral phrase, localized.
     *
     * <p>Public so a caller holding its own belt-and-braces guard against a blank reason can
     * substitute the same localized string the fallback would have produced, rather than
     * hardcoding one language of its own.
     *
     * @param language the requested language code; null or blank is tolerated
     * @return a non-blank, localized phrase
     */
    public String neutralReason(String language) {
        return text(NEUTRAL_REASON_KEY, localeFor(language));
    }

    private double score(Product source, Product candidate) {
        double total = 0.0;

        if (sharesOrigin(source, candidate)) {
            total += ORIGIN_WEIGHT;
        }
        if (sharesRoastLevel(source, candidate)) {
            total += ROAST_LEVEL_WEIGHT;
        }
        total += Math.min(sharedTastingNoteCount(source, candidate) * TASTING_NOTE_WEIGHT, MAX_TASTING_NOTE_SCORE);

        if (sharesProcessingMethod(source, candidate)) {
            total += PROCESSING_METHOD_WEIGHT;
        }
        if (sharesCategory(source, candidate)) {
            total += CATEGORY_WEIGHT;
        }

        return total;
    }

    private boolean isSameProduct(Product source, Product candidate) {
        return source.getId() != null && source.getId().equals(candidate.getId());
    }

    private List<String> matchedLabels(Product source, Product candidate, Locale locale) {
        // A translation name is only usable if it has text; the slug is the fallback either way,
        // so an empty label segment can never render as "Origin: " with nothing after it.
        List<String> labels = new ArrayList<>();

        if (sharesOrigin(source, candidate)) {
            labels.add(label(ORIGIN_LABEL_KEY, locale, originName(source.getOrigin(), locale)));
        }
        if (sharesRoastLevel(source, candidate)) {
            labels.add(label(ROAST_LABEL_KEY, locale, roastName(source.getCoffeeDetails().getRoastLevel(), locale)));
        }

        List<String> notes = sharedTastingNoteNames(source, candidate, locale);
        if (!notes.isEmpty()) {
            labels.add(label(NOTES_LABEL_KEY, locale, String.join(NOTE_SEPARATOR, notes)));
        }

        if (sharesProcessingMethod(source, candidate)) {
            labels.add(label(PROCESS_LABEL_KEY, locale,
                    processName(source.getCoffeeDetails().getProcessingMethod(), locale)));
        }
        if (sharesCategory(source, candidate)) {
            labels.add(label(CATEGORY_LABEL_KEY, locale, categoryName(source.getCategory(), locale)));
        }

        return labels;
    }

    // --- localized display names --------------------------------------------------------

    private String originName(Origin origin, Locale locale) {
        return displayName(origin.getTranslations(), origin.getSlug(), locale,
                OriginTranslation::getLanguage, OriginTranslation::getName);
    }

    private String categoryName(Category category, Locale locale) {
        return displayName(category.getTranslations(), category.getSlug(), locale,
                CategoryTranslation::getLanguage, CategoryTranslation::getName);
    }

    private String roastName(RoastLevel roastLevel, Locale locale) {
        return displayName(roastLevel.getTranslations(), roastLevel.getSlug(), locale,
                RoastLevelTranslation::getLanguage, RoastLevelTranslation::getName);
    }

    private String processName(ProcessingMethod method, Locale locale) {
        return displayName(method.getTranslations(), method.getSlug(), locale,
                ProcessingMethodTranslation::getLanguage, ProcessingMethodTranslation::getName);
    }

    private String tastingNoteName(TastingNote note, Locale locale) {
        return displayName(note.getTranslations(), note.getSlug(), locale,
                TastingNoteTranslation::getLanguage, TastingNoteTranslation::getName);
    }

    /**
     * The translation's name for {@code locale}, degrading to any translation and then to the
     * slug — the same resolution order {@code GlobalSearchService} uses, so a reason chip and a
     * search result can never disagree about what an origin is called.
     *
     * <p>A translation row whose name is blank is skipped rather than rendered: it is present in
     * the table but not usable as text, and an empty value would defeat the whole fallback.
     */
    private <T> String displayName(List<T> translations, String slug, Locale locale,
            Function<T, String> languageGetter, Function<T, String> nameGetter) {

        if (translations != null) {
            String exact = translations.stream()
                    .filter(translation -> StringUtils.hasText(languageGetter.apply(translation)))
                    .filter(translation -> languageGetter.apply(translation).equals(locale.getLanguage()))
                    .map(nameGetter)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse(null);

            if (exact != null) {
                return exact;
            }

            String anyLanguage = translations.stream()
                    .map(nameGetter)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse(null);

            if (anyLanguage != null) {
                return anyLanguage;
            }
        }

        return StringUtils.hasText(slug) ? slug : locale.getLanguage();
    }

    /**
     * Resolves a label from the bundle, falling back to the key itself.
     *
     * <p>Falling back to the key rather than to English text means a missing translation shows up
     * as an obviously broken {@code ai.similar.reason.*} label in the UI instead of silently
     * reverting to English on the Arabic storefront — the failure mode this class exists to
     * prevent.
     */
    private String label(String key, Locale locale, String value) {
        return messageSource.getMessage(key, new Object[] { value }, key, locale);
    }

    private String text(String key, Locale locale) {
        return messageSource.getMessage(key, null, key, locale);
    }

    /**
     * Turns a requested language code into a {@link Locale}.
     *
     * <p>Blank or null falls back to the request locale and then to {@link Locale#ENGLISH},
     * mirroring {@code AcceptHeaderLocaleResolver}'s own default, so a caller that forgets the
     * code still gets a valid resolution instead of an exception.
     */
    private Locale localeFor(String language) {
        if (!StringUtils.hasText(language)) {
            Locale request = LocaleContextHolder.getLocale();
            return request == null ? Locale.ENGLISH : request;
        }
        return Locale.forLanguageTag(language.trim());
    }

    // --- relation comparison -----------------------------------------------------------

    private boolean sharesOrigin(Product source, Product candidate) {
        return shares(source.getOrigin(), candidate.getOrigin(), Origin::getSlug);
    }

    private boolean sharesCategory(Product source, Product candidate) {
        return shares(source.getCategory(), candidate.getCategory(), Category::getSlug);
    }

    /**
     * Both {@code coffeeDetails} hops are null-guarded: the relation is absent on every
     * non-coffee product, so a merchandise candidate must not throw.
     */
    private boolean sharesRoastLevel(Product source, Product candidate) {
        return sharesCoffeeScoped(source, candidate,
                details -> details.getRoastLevel(), RoastLevel::getSlug);
    }

    private boolean sharesProcessingMethod(Product source, Product candidate) {
        return sharesCoffeeScoped(source, candidate,
                details -> details.getProcessingMethod(), ProcessingMethod::getSlug);
    }

    private <T> boolean shares(T sourceSide, T candidateSide, Function<T, String> slugGetter) {
        if (sourceSide == null || candidateSide == null) {
            return false;
        }
        return slugEquals(slugGetter.apply(sourceSide), slugGetter.apply(candidateSide));
    }

    /**
     * Compares a {@code coffeeDetails}-scoped relation on both sides. The inner
     * {@code CoffeeDetails} may itself be null, which {@link #shares} treats as no match.
     */
    private <R> boolean sharesCoffeeScoped(Product source, Product candidate,
            Function<CoffeeDetails, R> relationGetter, Function<R, String> slugGetter) {

        CoffeeDetails sourceDetails = source.getCoffeeDetails();
        CoffeeDetails candidateDetails = candidate.getCoffeeDetails();

        if (sourceDetails == null || candidateDetails == null) {
            return false;
        }
        return shares(relationGetter.apply(sourceDetails), relationGetter.apply(candidateDetails), slugGetter);
    }

    private <T> int sharedTastingNoteCount(Product source, Product candidate) {
        return sharedTastingNoteSlugs(source, candidate).size();
    }

    /**
     * The names of the tasting notes both products carry, in source order, localized.
     *
     * <p>Ordering follows the source product so the chip reads the same way on every request for
     * the same product.
     */
    private List<String> sharedTastingNoteNames(Product source, Product candidate, Locale locale) {
        List<TastingNote> shared = sharedTastingNotes(source, candidate);

        return shared.stream()
                .map(note -> tastingNoteName(note, locale))
                .distinct()
                .toList();
    }

    private List<TastingNote> sharedTastingNotes(Product source, Product candidate) {
        List<TastingNote> sourceNotes = source.getTastingNotes();
        List<TastingNote> candidateNotes = candidate.getTastingNotes();

        if (sourceNotes == null || candidateNotes == null) {
            return List.of();
        }

        return sourceNotes.stream()
                .filter(note -> note != null && StringUtils.hasText(note.getSlug()))
                .filter(note -> candidateNotes.stream()
                        .anyMatch(candidateNote -> candidateNote != null
                                && note.getSlug().equals(candidateNote.getSlug())))
                .toList();
    }

    /**
     * The tasting-note slugs both products carry, in source order.
     *
     * <p>An absent list is treated as no notes rather than dereferenced: the column is on the
     * product and every product type uses it, so it is only ever null on an instance built by
     * hand.
     */
    private List<String> sharedTastingNoteSlugs(Product source, Product candidate) {
        return sharedTastingNotes(source, candidate).stream()
                .map(TastingNote::getSlug)
                .toList();
    }

    /**
     * Slug equality on trimmed, non-blank values only.
     *
     * <p>A blank slug on either side is never a match: two absent values comparing equal would
     * make every unrelated product match on every relation at once.
     */
    private boolean slugEquals(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right) && left.trim().equals(right.trim());
    }
}