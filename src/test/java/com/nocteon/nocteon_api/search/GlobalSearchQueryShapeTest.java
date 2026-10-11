package com.nocteon.nocteon_api.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.category.repository.CategoryRepository;
import com.nocteon.nocteon_api.journal.repository.JournalPostRepository;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.promoCode.repository.PromoCodeRepository;

/**
 * Structural rules for the dashboard search's repository queries.
 *
 * <p>These assert the <em>shape</em> of each query, because the suite mocks repositories and so
 * never executes a single one of them. Whether the JPQL actually translates, and whether the
 * ranking produces the intended order, can only be proven against a live database — but two of the
 * mistakes that produce a wrong dashboard are visible without one.
 */
class GlobalSearchQueryShapeTest {

    /**
     * Each search type with the repository that owns it and the pair of query methods.
     *
     * <p>Triples, not two parallel lists: pairing every repository with every method name would
     * look for searchCategories on ProductRepository and report a finding that is really a bug in
     * the test.
     */
    private static final List<Object[]> SEARCHES = List.of(
            new Object[] { ProductRepository.class, "searchProducts", "countSearchProducts" },
            new Object[] { CategoryRepository.class, "searchCategories", "countSearchCategories" },
            new Object[] { JournalPostRepository.class, "searchJournalPosts",
                    "countSearchJournalPosts" },
            new Object[] { PromoCodeRepository.class, "searchPromoCodes", "countSearchPromoCodes" },
            new Object[] { OrderRepository.class, "searchOrders", "countSearchOrders" },
            new Object[] { UserRepository.class, "searchUsers", "countSearchUsers" });

    private static Method methodOf(Class<?> repository, String name, Class<?>... extra) {
        Class<?>[] signature = new Class<?>[1 + extra.length];
        signature[0] = String.class;
        System.arraycopy(extra, 0, signature, 1, extra.length);
        try {
            return repository.getDeclaredMethod(name, signature);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    repository.getSimpleName() + " has no " + name, e);
        }
    }

    /** The row query takes a Pageable; the count query does not. */
    private static Method rowMethod(Class<?> repository, String name) {
        return methodOf(repository, name, Pageable.class);
    }

    /**
     * Every row query must state its own order.
     *
     * <p>These queries are called with an unsorted {@code Pageable} precisely so the query's
     * {@code ORDER BY} survives; if a query loses its ordering, the capped page silently becomes
     * whichever five rows the database felt like returning, and an exact match can be dropped in
     * favour of a row that merely contains the same letters.
     */
    @Test
    @DisplayName("every search row query carries its own relevance ordering")
    void everySearchRowQueryIsRanked() {
        List<String> unranked = new ArrayList<>();

        for (Object[] search : SEARCHES) {
            Class<?> repository = (Class<?>) search[0];
            String name = (String) search[1];
            Query query = rowMethod(repository, name).getAnnotation(Query.class);
            if (query == null) {
                unranked.add(repository.getSimpleName() + "." + name + " has no @Query");
            } else if (!query.value().toUpperCase().contains("ORDER BY")) {
                unranked.add(repository.getSimpleName() + "." + name);
            }
        }

        assertThat(unranked)
                .as("""
                        Search queries with no ORDER BY.

                        Each type is capped at five rows and the Pageable is passed unsorted so the \
                        query's own ordering survives, which only helps if the query has one. Without \
                        it the dashboard shows an arbitrary five.""")
                .isEmpty();
    }

    /**
     * A row query may not use {@code DISTINCT} alongside a computed {@code ORDER BY}.
     *
     * <p>PostgreSQL rejects this outright: {@code for SELECT DISTINCT, ORDER BY expressions must
     * appear in select list}. Ranking a joined query requires {@code GROUP BY} on the primary key
     * instead, which is what these queries use.
     */
    @Test
    @DisplayName("no search row query combines DISTINCT with a computed order")
    void noSearchQueryCombinesDistinctWithAComputedOrder() {
        List<String> offenders = new ArrayList<>();

        for (Object[] search : SEARCHES) {
            Class<?> repository = (Class<?>) search[0];
            String name = (String) search[1];
            String jpql = rowMethod(repository, name)
                    .getAnnotation(Query.class).value().toUpperCase();
            boolean distinct = jpql.contains("SELECT DISTINCT");
            boolean computedOrder = jpql.contains("ORDER BY MIN(") || jpql.contains("ORDER BY CASE");

            if (distinct && computedOrder) {
                offenders.add(repository.getSimpleName() + "." + name);
            }
        }

        assertThat(offenders)
                .as("""
                        Queries PostgreSQL will refuse.

                        SELECT DISTINCT cannot be ordered by an expression that is not in the select \
                        list, and a relevance rank is exactly such an expression. This fails at \
                        execution time, not startup, so it would surface as a 500 from the search \
                        endpoint and nowhere else.""")
                .isEmpty();
    }

    /**
     * Row and count must agree on what they match.
     *
     * <p>The rows are what the dashboard lists; the count is what it reports. If the two filter
     * differently, the reported number describes a set of rows the dashboard never looked at,
     * which is the defect this change exists to remove.
     *
     * <p>Only the WHERE clause is compared. Comparing the whole query is too coarse to be useful:
     * a ranked query mentions its slug several times — in the WHERE, in the rank expression and in
     * the tie-break — so deleting one WHERE branch leaves the field "mentioned" and the check passes
     * on a query that is now genuinely wrong.
     */
    @Test
    @DisplayName("each count query filters on the same fields as its row query")
    void countQueriesMatchTheirRowQueries() {
        List<String> drifted = new ArrayList<>();

        for (Object[] search : SEARCHES) {
            Class<?> repository = (Class<?>) search[0];
            String rowName = (String) search[1];
            String countName = (String) search[2];
            Query rows = rowMethod(repository, rowName).getAnnotation(Query.class);
            Query counts = methodOf(repository, countName).getAnnotation(Query.class);

            Set<String> rowFilters = matchedFields(rows.value());
            Set<String> countFilters = matchedFields(counts.value());

            if (!rowFilters.equals(countFilters)) {
                drifted.add(repository.getSimpleName() + "." + rowName
                        + " matches " + rowFilters
                        + " but " + countName + " matches " + countFilters);
            }
        }

        assertThat(drifted)
                .as("""
                        Row and count queries filtering on different fields.

                        The rows are what the dashboard lists and the count is what it reports, so a \
                        disagreement between them makes the reported number describe rows that were \
                        never considered.""")
                .isEmpty();
    }

    /** The clause that ends a WHERE list, at the start of a line. */
    private static final Pattern CLAUSE_END = Pattern.compile(
            "^\\s*(GROUP\\s+BY|ORDER\\s+BY)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    /** JPQL/SQL words that carry no field meaning and must not count as "a field queried". */
    private static final Set<String> SQL_NOISE = Set.of(
            "where", "and", "or", "not", "like", "ilike", "lower", "upper", "concat", "case",
            "when", "then", "else", "end", "is", "null", "true", "false", "asc", "desc",
            "distinct", "select", "from", "join", "on", "as", "in", "exists", "any", "some",
            "all", "between", "having", "group", "order", "by", "count", "min", "max", "sum",
            "cast", "string", "query");

    /**
     * The identifiers a query's WHERE clause refers to.
     *
     * <p>Deliberately token-based rather than "the identifier just before LIKE": these queries wrap
     * every field as {@code LOWER(field) LIKE ...}, so the character before LIKE is a closing
     * parenthesis and an adjacency pattern matches nothing at all.
     *
     * <p>{@code CAST(o.id AS string)} is why {@code string} is on the noise list — and why
     * {@code order.id} counts as a field while {@code id} alone does not.
     */
    private static Set<String> matchedFields(String query) {
        int from = query.toUpperCase().indexOf("WHERE ");
        if (from < 0) {
            return Set.of();
        }
        String where = query.substring(from);

        // Stop at the clause that ends the filter list, so the rank expression and the tie-break —
        // which mention the same fields for other reasons — are not counted.
        //
        // Matched with a newline-anchored regex rather than indexOf(" ORDER BY "): a Java text
        // block strips its common indentation, so in the compiled annotation these clauses are
        // preceded by a newline and no space, and a space-prefixed search silently finds nothing.
        Matcher terminator = CLAUSE_END.matcher(where);
        if (terminator.find()) {
            where = where.substring(0, terminator.start());
        }

        Matcher matcher = Pattern.compile("\\b[a-z][a-z_]*(?:\\.[a-z_][a-z_]*)*\\b")
                .matcher(where.toLowerCase());

        Set<String> fields = new TreeSet<>();
        while (matcher.find()) {
            if (!SQL_NOISE.contains(matcher.group())) {
                fields.add(matcher.group());
            }
        }
        return fields;
    }

    /** The count methods must exist at all; a missing one silently drops a type from the total. */
    @Test
    @DisplayName("every search type has a matching count query")
    void everySearchTypeHasACountQuery() {
        for (Object[] search : SEARCHES) {
            Class<?> repository = (Class<?>) search[0];
            String countName = (String) search[2];
            Method count = methodOf(repository, countName);
            assertThat(count.getAnnotation(Query.class))
                    .as("%s.%s must declare a @Query", repository.getSimpleName(), countName)
                    .isNotNull();
            assertThat(count.getParameters())
                    .as("%s.%s takes only the query text", repository.getSimpleName(), countName)
                    .hasSize(1);
        }
    }
}