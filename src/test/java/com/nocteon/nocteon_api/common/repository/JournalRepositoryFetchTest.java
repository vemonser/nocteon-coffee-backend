package com.nocteon.nocteon_api.common.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.journal.entity.JournalPost;

/**
 * Hibernate cannot fetch two bag collections in a single query.
 *
 * <p>It throws {@code MultipleBagFetchException} when it does, which means the query fails at
 * execution time rather than at startup — so the endpoint compiles, deploys, and 500s on every
 * request. {@code JournalPostRepository} did exactly this: both slug queries carried
 * {@code LEFT JOIN FETCH p.translations} and {@code LEFT JOIN FETCH p.products}, and since both
 * fields are {@code List}, {@code GET /api/journal/{slug}} returned 500 for every slug. It was
 * invisible while the route sat behind a 401, and became publicly reachable the moment the
 * permitAll list was corrected.
 *
 * <p>The suite mocks repositories, so no test exercised the query. This asserts the shape instead:
 * at most one {@code List}-typed association may be fetch-joined per query.
 */
class JournalRepositoryFetchTest {

    /** {@code LEFT JOIN FETCH <path>} / {@code JOIN FETCH <path>}. */
    private static final Pattern FETCH_JOIN = Pattern.compile("JOIN\\s+FETCH\\s+([\\w.]+)");

    @Test
    @DisplayName("no query fetch-joins two bag collections, which Hibernate refuses to do")
    void noQueryFetchesTwoBags() {
        List<String> offendingQueries = new ArrayList<>();

        for (Method method : com.nocteon.nocteon_api.journal.repository.JournalPostRepository.class
                .getDeclaredMethods()) {
            Query query = method.getAnnotation(Query.class);
            if (query == null) {
                continue;
            }

            List<String> bags = new ArrayList<>();
            Matcher matcher = FETCH_JOIN.matcher(query.value());
            while (matcher.find()) {
                String association = matcher.group(1);
                if (isBag(association)) {
                    bags.add(association);
                }
            }

            if (bags.size() > 1) {
                offendingQueries.add(method.getName() + " fetch-joins " + bags);
            }
        }

        assertThat(offendingQueries)
                .as("""
                        Queries that fetch more than one List-typed association.

                        Hibernate throws MultipleBagFetchException for these at execution time, so \
                        the endpoint 500s on every request while still compiling and deploying. Set \
                        the association, or let it lazy-load and accept the extra query.""")
                .isEmpty();
    }

    /**
     * Whether the fetch-joined path names a {@code List} on {@link JournalPost}.
     *
     * <p>Only {@link JournalPost} is checked because it is the entity whose detail queries exist;
     * the point is the two-bag rule, not a general entity audit.
     */
    private static boolean isBag(String association) {
        String fieldName = association.substring(association.lastIndexOf('.') + 1);
        try {
            Type type = JournalPost.class.getDeclaredField(fieldName).getGenericType();
            if (type instanceof ParameterizedType parameterized) {
                Type raw = parameterized.getRawType();
                return raw == List.class || raw == Collection.class;
            }
            return false;
        } catch (NoSuchFieldException e) {
            // A fetch through a nested path (e.g. products.category) is not a collection on the
            // root; not enough signal to call it a bag, so do not report it.
            return false;
        }
    }
}