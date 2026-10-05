package com.nocteon.nocteon_api.product.repository.specification;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethod;
import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethodTranslation;
import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.category.entity.CategoryTranslation;
import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVariety;
import com.nocteon.nocteon_api.coffeeVariety.entity.CoffeeVarietyTranslation;
import com.nocteon.nocteon_api.farm.entity.Farm;
import com.nocteon.nocteon_api.farm.entity.FarmTranslation;
import com.nocteon.nocteon_api.origin.entity.Origin;
import com.nocteon.nocteon_api.origin.entity.OriginTranslation;
import com.nocteon.nocteon_api.product.entity.CoffeeDetails;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductBrewingMethod;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethod;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethodTranslation;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevel;
import com.nocteon.nocteon_api.roastLevel.entity.RoastLevelTranslation;
import com.nocteon.nocteon_api.tastingNote.entity.TastingNote;
import com.nocteon.nocteon_api.tastingNote.entity.TastingNoteTranslation;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * The free-text half of {@link ProductSpecifications#withFilters}.
 *
 * <h2>Why this is not a {@code LIKE} on the product name</h2>
 *
 * The predicate this replaces matched the <em>whole</em> query string against
 * {@code product_translations.name} only. That cannot answer a natural-language query, because the
 * words a customer types are not the words the catalog stores: {@code "ethiopian coffee"} looks for
 * the literal substring {@code ethiopian coffee} in a product named {@code Ethiopia Yirgacheffe},
 * where the country lives in {@code origins.slug} and the noun in {@code categories.slug}; and on
 * the {@code ar} locale the product name is Arabic, so an English query matched nothing at all.
 *
 * <h2>What a term has to match</h2>
 *
 * The query is split into terms (see {@link #tokenize(String)}), and a product matches when
 * <b>every</b> significant term matches <b>some</b> searchable attribute — an {@code AND} across
 * terms, an {@code OR} across attributes. Both halves matter: the {@code AND} is what keeps
 * {@code "ethiopian coffee"} from degrading into "every product in the coffee category", and the
 * {@code OR} is what lets a single word reach the attribute that actually holds it.
 *
 * <p>The attributes, and how each is reached:
 *
 * <ul>
 * <li>{@code translations.name} / {@code short_description} / {@code description} — read off the
 * {@code translations} join {@link ProductSpecifications} already has, in the requested language.</li>
 * <li>{@code products.slug}, {@code categories.slug}, {@code origins.slug}, {@code farms.slug} —
 * off the root and the three to-one joins already present. Slugs are ASCII and
 * locale-independent, which is what keeps an English query useful on the {@code ar} locale.</li>
 * <li>{@code origins/farms/categories}.{@code translations.name},
 * {@code coffee_details → roast_level | processing_method | coffee_variety} (slug and translated
 * name), {@code tasting_notes} (slug and translated name) and
 * {@code product_brewing_methods → brewing_methods} (slug and translated name) — behind six
 * {@code EXISTS} subqueries: one per relation, except the three coffee lookups, which all hang off
 * the same {@code coffee_details} row and share one.</li>
 * </ul>
 *
 * <p>A term is matched by more than one {@code LIKE} shape, because substring matching alone is not
 * enough to reach the catalog. See {@link #likePatterns(String)}.
 *
 * <h2>Why the multi-valued relations are subqueries and not more joins</h2>
 *
 * The outer query already joins {@code translations} and {@code variants}, which is why
 * {@link ProductSpecifications#applyOrdering} pairs {@code groupBy(products.id)} with an aggregate,
 * or falls back to {@code distinct}, to survive the duplicated rows. {@code tasting_notes} and
 * {@code product_brewing_methods} are to-many, so join-fetching them for the search text would
 * multiply the row count by every note and every brew method of every product <em>before</em>
 * pagination — exactly the regression {@code groupBy}/{@code distinct} exist to absorb, and
 * multiplying further makes a page of 20 cost a scan over tens of thousands of rows. An
 * {@code EXISTS} subquery answers "does this product have a matching note" without adding a single
 * row to the outer result, so the cardinality, the aggregate ordering and the page boundaries are
 * all exactly what they were before this class existed. Nothing here joins when there is no search
 * text: the predicate is built inside the {@code if}, and the {@code FROM} clause of a plain
 * catalog listing is untouched.
 *
 * <h2>Locale</h2>
 *
 * The {@code translations} join keeps its single language predicate, so the product name is still
 * read in the requested language and {@link ProductSpecifications#applyOrdering} still sorts on it.
 * Every <em>additional</em> translation the search reads is pinned to the same language inside its
 * own subquery, so nothing widens past the requested locale; the slug branches are deliberately not
 * pinned, because a slug is the one identifier that means the same thing in every language.
 *
 * <p>This is the fallback path. It answers what the words in the query literally mean, which is why
 * a query with no lexical counterpart for one of its terms ({@code "fruity"}, {@code "pour over"})
 * can still come back empty — that gap is what vector search exists to close, and this class is
 * what that path degrades to.
 */
final class ProductSearchPredicate {

    /**
     * Words that are grammatical glue or shop furniture rather than product attributes. Dropping
     * them is what turns {@code "best for espresso"} into a single-term query instead of a
     * three-term one that no product could ever satisfy.
     *
     * <p>{@code coffee} and {@code قهوة} are deliberately <em>not</em> here:
     * {@code categories.slug} is {@code coffee}, so keeping the word narrows
     * {@code "ethiopian coffee"} to the coffee category instead of the equipment catalogue.
     */
    private static final Set<String> STOP_WORDS = Set.of(
            // English function words.
            "a", "an", "and", "are", "as", "at", "be", "been", "but", "by", "can", "could", "did",
            "do", "does", "for", "from", "had", "has", "have", "he", "her", "here", "hers", "him",
            "his", "how", "i", "if", "in", "into", "is", "it", "its", "just", "like", "me", "mine",
            "most", "much", "must", "my", "need", "no", "not", "of", "on", "one", "only", "or",
            "other", "our", "out", "own", "please", "same", "she", "should", "so", "some", "such",
            "than", "that", "the", "their", "them", "then", "there", "these", "they", "this",
            "those", "to", "too", "up", "us", "very", "want", "was", "we", "were", "what", "when",
            "where", "which", "while", "who", "whom", "why", "will", "with", "would", "you", "your",
            // English filler a shopper types that names no attribute in this catalog.
            "bag", "bags", "bean", "beans", "best", "better", "buy", "cheap", "cup", "cups",
            "level", "levels", "method", "methods", "note", "notes", "order", "orders", "price",
            "prices", "product", "products", "quality", "recommend", "recommended", "shop",
            "suitable", "top",
            // Arabic function words.
            "أن", "أو", "إلى", "التي", "الذي", "الذين", "ثم", "هذا", "هذه", "ذلك", "تلك", "نحن",
            "به", "بهذه", "بين", "حول", "خلال", "حيث", "على", "عن", "في", "قد", "كان", "كانت",
            "كل", "كما", "لا", "لكن", "ما", "مع", "من", "هنا", "هل", "هو", "هي", "و", "يعني",
            "يمكن",
            // Arabic filler with no counterpart in this catalog.
            "أكواب", "أرخص", "أفضل", "أريد", "أبحث", "أحتاج", "أعطني", "جودة", "حبوب", "دور",
            "سعر", "طريقة", "طرق", "كوب", "متجر", "مستوى", "مستويات", "منتج", "منتجات",
            "مناسب", "مناسبة", "نكهة", "نكهات");

    /**
     * Arabic clitics a shopper glues onto the front of a noun: {@code بالشوكولاتة} is
     * {@code شوكولاتة} with a preposition and the definite article. Longest first, so the two-letter
     * forms are only tried once the three-letter ones have failed.
     */
    private static final List<String> ARABIC_CLITICS = List.of(
            "وال", "بال", "فال", "ال", "لل", "و", "ف", "ب", "ك", "ل");

    /** Anything outside a letter or a digit separates terms. Unicode, so Arabic splits too. */
    private static final Pattern TERM_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+");

    /**
     * How many leading characters of a term are also matched as a prefix. This is the whole reason
     * {@code "ethiopian"} can reach {@code origins.slug = 'ethiopia'} and {@code "chocolatey"} can
     * reach {@code tasting_notes.slug = 'chocolate'}: neither word is a substring of the stored
     * one, but both start with it. Four characters is short enough to survive
     * {@code ethiopian → ethiopia} and {@code citrusy → citrus}, and long enough that ordinary
     * words are not mangled into nonsense.
     */
    private static final int PREFIX_LENGTH = 4;

    /** Terms shorter than this are noise, not search terms. */
    private static final int MIN_TERM_LENGTH = 2;

    /**
     * Upper bound on the terms one query contributes. The predicate is linear in this number — every
     * term builds one {@code LIKE} per direct attribute and one {@code EXISTS} per related
     * collection — so an unbounded query would produce an unbounded {@code WHERE} clause. Five
     * covers anything a search box realistically receives.
     */
    private static final int MAX_TERMS = 5;

    private ProductSearchPredicate() {
    }

    /**
     * The predicate for {@code search}, or {@code null} when the query has nothing searchable in it
     * (blank, or punctuation only) and the caller should add no predicate at all.
     */
    static Predicate build(
            CriteriaBuilder cb,
            CriteriaQuery<?> query,
            Root<Product> root,
            String language,
            String search,
            Join<Product, ProductTranslation> translations,
            Join<Product, Category> category,
            Join<Product, Origin> origin,
            Join<Product, Farm> farm) {

        List<String> terms = significantTerms(search);
        if (terms.isEmpty()) {
            return null;
        }

        // Attributes already reachable through the joins ProductSpecifications builds. Reading them
        // off those aliases is what keeps the search from adding a single row to the outer query.
        List<Expression<String>> direct = List.of(
                translations.<String>get("name"),
                translations.<String>get("shortDescription"),
                translations.<String>get("description"),
                root.<String>get("slug"),
                category.<String>get("slug"),
                origin.<String>get("slug"),
                farm.<String>get("slug"));

        List<Predicate> perTerm = new ArrayList<>();
        for (String term : terms) {
            List<String> patterns = likePatterns(term);

            List<Predicate> anywhere = new ArrayList<>();
            for (Expression<String> path : direct) {
                anywhere.add(anyLike(cb, path, patterns));
            }
            anywhere.add(ownedTranslationExists(cb, query, origin, OriginTranslation.class, "origin", language, patterns));
            anywhere.add(ownedTranslationExists(cb, query, farm, FarmTranslation.class, "farm", language, patterns));
            anywhere.add(ownedTranslationExists(cb, query, category, CategoryTranslation.class, "category", language, patterns));
            anywhere.add(coffeeDetailsExists(cb, query, root, language, patterns));
            anywhere.add(tastingNoteExists(cb, query, root, language, patterns));
            anywhere.add(brewingMethodExists(cb, query, root, language, patterns));

            perTerm.add(cb.or(anywhere.toArray(new Predicate[0])));
        }

        return cb.and(perTerm.toArray(new Predicate[0]));
    }

    /**
     * The terms a query contributes: lowercased, split, de-duplicated, stop-worded, capped.
     *
     * <p>When every term is a stop word the original terms are used anyway. Dropping them all would
     * turn a query like {@code "coffee"} into "no search at all" and hand the caller the entire
     * catalog, and a wide answer is a better failure than an empty one.
     */
    static List<String> significantTerms(String search) {
        List<String> tokens = tokenize(search);
        if (tokens.isEmpty()) {
            return List.of();
        }

        List<String> significant = tokens.stream().filter(term -> !STOP_WORDS.contains(term)).toList();
        List<String> terms = significant.isEmpty() ? tokens : significant;

        return terms.size() > MAX_TERMS ? List.copyOf(terms.subList(0, MAX_TERMS)) : terms;
    }

    /**
     * Lowercased, punctuation-free, de-duplicated terms in the order the shopper typed them.
     *
     * <p>NFKC first, so text pasted from a phone — full-width Latin, decomposed accents — compares
     * against the ASCII the catalog stores. Order is preserved because the first terms are the ones
     * a shopper typed first, and they are the ones worth keeping when {@link #MAX_TERMS} truncates.
     */
    static List<String> tokenize(String search) {
        if (!StringUtils.hasText(search)) {
            return List.of();
        }

        String normalized = Normalizer.normalize(search.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);

        Set<String> tokens = new LinkedHashSet<>();
        for (String token : TERM_SEPARATOR.split(normalized)) {
            if (token.length() >= MIN_TERM_LENGTH) {
                tokens.add(token);
            }
        }

        return List.copyOf(tokens);
    }

    /**
     * The {@code LIKE} patterns one term is matched by, most specific first.
     *
     * <p>Three shapes, and each covers a case the other two miss: {@code %term%} is a plain
     * substring match; {@code prefix%} reaches a stored word the term extends
     * ({@code ethiopian} → {@code ethiopia}); and the clitic-stripped variants reach an Arabic word
     * the term decorates ({@code بالشوكولاتة} → {@code شوكولاتة}).
     */
    static List<String> likePatterns(String term) {
        Set<String> patterns = new LinkedHashSet<>();
        addPatterns(patterns, term);
        for (String stripped : stripArabicClitics(term)) {
            addPatterns(patterns, stripped);
        }
        return List.copyOf(patterns);
    }

    private static void addPatterns(Set<String> patterns, String term) {
        if (term.isEmpty()) {
            return;
        }
        patterns.add("%" + term + "%");
        if (term.length() > PREFIX_LENGTH) {
            patterns.add(term.substring(0, PREFIX_LENGTH) + "%");
        }
    }

    /** The term with a leading Arabic clitic removed, when removing it leaves something matchable. */
    private static List<String> stripArabicClitics(String term) {
        for (String clitic : ARABIC_CLITICS) {
            if (term.startsWith(clitic) && term.length() - clitic.length() >= MIN_TERM_LENGTH + 1) {
                return List.of(term.substring(clitic.length()));
            }
        }
        return List.of();
    }

    /** {@code lower(path) LIKE any pattern}, which is how one term reaches one attribute. */
    private static Predicate anyLike(CriteriaBuilder cb, Expression<String> path, List<String> patterns) {
        Expression<String> lowered = cb.lower(path);
        Predicate[] likes = patterns.stream()
                .map(pattern -> cb.like(lowered, pattern))
                .toArray(Predicate[]::new);
        return cb.or(likes);
    }

    /**
     * The translated name of a product's origin, farm or category.
     *
     * <p>Correlated on the to-one join the outer query already holds, so the subquery is a keyed
     * lookup on a {@code *_translations} primary key and contributes no rows to the outer query.
     */
    private static <T> Predicate ownedTranslationExists(
            CriteriaBuilder cb,
            CriteriaQuery<?> query,
            Expression<?> owner,
            Class<T> translationType,
            String ownerAttribute,
            String language,
            List<String> patterns) {

        Subquery<Long> subquery = query.subquery(Long.class);
        Root<T> translation = subquery.from(translationType);

        subquery.select(translation.<Long>get("id"));
        subquery.where(
                cb.equal(translation.<Object>get(ownerAttribute), owner),
                cb.equal(translation.<String>get("language"), language),
                anyLike(cb, translation.<String>get("name"), patterns));

        return cb.exists(subquery);
    }

    /**
     * Roast level, processing method and variety, by slug or translated name.
     *
     * <p>All three live in one {@code EXISTS} rather than three. They are all reached from the same
     * {@code coffee_details} row, and an existence check does not care how many rows its own joins
     * produce — so one subquery answers "does this product's coffee detail match?" for all three at
     * once, and the outer query pays for one semi-join instead of three.
     *
     * <p>It is an {@code EXISTS} rather than a join on the outer query for the reason that matters:
     * {@code coffee_details} is a to-one, but the translations hanging off its lookups are to-many,
     * and every extra to-many in the outer query multiplies the rows pagination pages over.
     */
    private static Predicate coffeeDetailsExists(
            CriteriaBuilder cb,
            CriteriaQuery<?> query,
            Root<Product> root,
            String language,
            List<String> patterns) {

        Subquery<Long> subquery = query.subquery(Long.class);
        Root<CoffeeDetails> details = subquery.from(CoffeeDetails.class);

        Join<CoffeeDetails, RoastLevel> roastLevel = details.join(RoastLevel.class, JoinType.LEFT);
        Join<CoffeeDetails, ProcessingMethod> processingMethod =
                details.join(ProcessingMethod.class, JoinType.LEFT);
        Join<CoffeeDetails, CoffeeVariety> coffeeVariety =
                details.join(CoffeeVariety.class, JoinType.LEFT);

        subquery.select(details.<Long>get("id"));
        subquery.where(
                cb.equal(details.<Object>get("product"), root),
                cb.or(
                        anyLike(cb, roastLevel.<String>get("slug"), patterns),
                        anyLike(cb, processingMethod.<String>get("slug"), patterns),
                        anyLike(cb, coffeeVariety.<String>get("slug"), patterns),
                        translatedNameMatches(cb, roastLevel.join(RoastLevelTranslation.class, JoinType.LEFT),
                                language, patterns),
                        translatedNameMatches(cb,
                                processingMethod.join(ProcessingMethodTranslation.class, JoinType.LEFT),
                                language, patterns),
                        translatedNameMatches(cb, coffeeVariety.join(CoffeeVarietyTranslation.class, JoinType.LEFT),
                                language, patterns)));

        return cb.exists(subquery);
    }

    /**
     * A translation's name in one language. The language pin is inside the OR rather than in the
     * subquery's own {@code where}, because a product whose roast level slug matches must qualify
     * even when this product has no translation in the requested language at all.
     */
    private static <T> Predicate translatedNameMatches(
            CriteriaBuilder cb,
            Join<?, T> translation,
            String language,
            List<String> patterns) {

        return cb.and(
                cb.equal(translation.<String>get("language"), language),
                anyLike(cb, translation.<String>get("name"), patterns));
    }

    /**
     * A tasting note, by slug or translated name. {@code tasting_notes} is a to-many on
     * {@link Product}, so this subquery is what keeps the search from multiplying rows.
     */
    private static Predicate tastingNoteExists(
            CriteriaBuilder cb,
            CriteriaQuery<?> query,
            Root<Product> root,
            String language,
            List<String> patterns) {

        Subquery<Long> subquery = query.subquery(Long.class);
        Root<Product> product = subquery.from(Product.class);
        Join<Product, TastingNote> note = product.join("tastingNotes", JoinType.LEFT);

        subquery.select(product.<Long>get("id"));
        subquery.where(
                cb.equal(product.<Object>get("id"), root.get("id")),
                cb.or(
                        anyLike(cb, note.<String>get("slug"), patterns),
                        translatedNameMatches(cb, note.join(TastingNoteTranslation.class, JoinType.LEFT),
                                language, patterns)));

        return cb.exists(subquery);
    }

    /**
     * A brewing method, by slug or translated name.
     *
     * <p>Deliberately a second, independent route to {@code product_brewing_methods}: the outer query
     * joins that table when the caller filters by brew method, and reusing that join would couple
     * the search to a filter that may not be set.
     */
    private static Predicate brewingMethodExists(
            CriteriaBuilder cb,
            CriteriaQuery<?> query,
            Root<Product> root,
            String language,
            List<String> patterns) {

        Subquery<Long> subquery = query.subquery(Long.class);
        Root<Product> product = subquery.from(Product.class);
        Join<Product, ProductBrewingMethod> link = product.join("brewingMethods", JoinType.LEFT);
        Join<ProductBrewingMethod, BrewingMethod> method = link.join("brewingMethod", JoinType.LEFT);

        subquery.select(product.<Long>get("id"));
        subquery.where(
                cb.equal(product.<Object>get("id"), root.get("id")),
                cb.or(
                        anyLike(cb, method.<String>get("slug"), patterns),
                        translatedNameMatches(cb, method.join(BrewingMethodTranslation.class, JoinType.LEFT),
                                language, patterns)));

        return cb.exists(subquery);
    }
}