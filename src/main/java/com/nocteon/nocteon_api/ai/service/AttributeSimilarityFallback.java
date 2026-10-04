package com.nocteon.nocteon_api.ai.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.origin.entity.Origin;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethod;
import com.nocteon.nocteon_api.product.entity.CoffeeDetails;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevel;
import com.nocteon.nocteon_api.tastingNote.entity.TastingNote;

/**
 * Scores catalog products by how many tasting attributes they share with a source product.
 *
 * <p>The safety net under the vector path. When Ollama is unreachable, the feature flag is off,
 * or nothing has been indexed, {@link SimilarProductService} still has to answer
 * "what else should I look at" — and plain attribute overlap answers that well enough for
 * coffee, where origin, roast, process and notes genuinely drive substitution. It is not a
 * ranking model and does not pretend to be one.
 *
 * <h2>Why it is field-free and side-effect free</h2>
 * No collaborators, no I/O, no clock, no randomness: everything arrives as arguments. That is
 * what makes the net independently testable — the fallback's own tests run with no mocks at
 * all, so they still pass when the entire AI path is broken, which is exactly the moment they
 * matter.
 *
 * <h2>Why it compares slugs and not entities</h2>
 * Relations are compared by {@code slug}, never by object identity or id. JPA hands out a
 * different instance per persistence context, so identity comparison would score every
 * candidate at zero; and ids cannot be used to <em>compare</em> relations because they would be
 * null for anything built outside a session. Slugs are stable, ASCII and locale-independent, so
 * the same comparison also works on detached instances loaded in two different queries.
 *
 * <h2>The weights</h2>
 * Origin {@code +0.4}, roast level {@code +0.2}, each shared tasting note {@code +0.15} capped
 * in total at {@code +0.3}, processing method {@code +0.1}, category {@code +0.1}.
 *
 * <p>Origin dominates because it is the attribute that most reliably predicts whether a second
 * bag of coffee will be liked by someone who liked the first — a washed Ethiopian and a washed
 * Colombian are both answers to the same craving, while sharing a category does not make one a
 * substitute for the other. Tasting notes are capped rather than summed freely because
 * otherwise a candidate listing five common notes would outrank an exact-origin match, and
 * note overlap is the least discriminating signal of the five: nearly every specialty coffee
 * has something berry in it.
 */
@Component
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

    /** Shown when nothing at all is shared, so the chip is never empty. */
    private static final String NEUTRAL_REASON = "More from our collection";

    private static final String REASON_SEPARATOR = " · ";

    /**
     * Scores every candidate against the source.
     *
     * <p>The source is excluded by id — a "similar products" rail that leads with the product
     * the customer is already looking at is worse than one that shows nothing.
     *
     * <p>Insertion order is the candidate order, and the scores are returned raw and
     * unclamped: {@code 0.0} is a legitimate result meaning "nothing shared", and the caller
     * ranks and truncates.
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
     * Describes why a candidate was offered, in the customer's terms.
     *
     * <p>Always returns something renderable. The response contract promises a non-blank
     * {@code reason} on every item, and an empty string would surface in the UI as a blank chip
     * that looks like a rendering bug — so a zero score, an unknown match set, or a pair
     * sharing nothing all collapse to a neutral phrase rather than to {@code ""}.
     *
     * @param source    the product being viewed; never {@code null}
     * @param candidate the product being offered; never {@code null}
     * @param score     the score the pair was given; used only to decide whether an explanation
     *                  is owed at all
     * @return a non-blank, human-readable reason
     */
    public String reason(Product source, Product candidate, double score) {
        if (score <= 0.0) {
            return NEUTRAL_REASON;
        }

        List<String> labels = matchedLabels(source, candidate);

        return labels.isEmpty() ? NEUTRAL_REASON : String.join(REASON_SEPARATOR, labels);
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

    private List<String> matchedLabels(Product source, Product candidate) {
        List<String> labels = new ArrayList<>();

        if (sharesOrigin(source, candidate)) {
            labels.add("Origin: " + source.getOrigin().getSlug());
        }
        if (sharesRoastLevel(source, candidate)) {
            labels.add("Roast: " + source.getCoffeeDetails().getRoastLevel().getSlug());
        }

        List<String> notes = sharedTastingNoteSlugs(source, candidate);
        if (!notes.isEmpty()) {
            labels.add("Notes: " + String.join(", ", notes));
        }

        if (sharesProcessingMethod(source, candidate)) {
            labels.add("Process: " + source.getCoffeeDetails().getProcessingMethod().getSlug());
        }
        if (sharesCategory(source, candidate)) {
            labels.add("Category: " + source.getCategory().getSlug());
        }

        return labels;
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
     * The tasting-note slugs both products carry, in source order.
     *
     * <p>An absent list is treated as no notes rather than dereferenced: the column is on the
     * product and every product type uses it, so it is only ever null on an instance built by
     * hand.
     */
    private List<String> sharedTastingNoteSlugs(Product source, Product candidate) {
        List<TastingNote> sourceNotes = source.getTastingNotes();
        List<TastingNote> candidateNotes = candidate.getTastingNotes();

        if (sourceNotes == null || candidateNotes == null) {
            return List.of();
        }

        return sourceNotes.stream()
                .map(TastingNote::getSlug)
                .filter(StringUtils::hasText)
                .filter(slug -> candidateNotes.stream().map(TastingNote::getSlug).anyMatch(slug::equals))
                .distinct()
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