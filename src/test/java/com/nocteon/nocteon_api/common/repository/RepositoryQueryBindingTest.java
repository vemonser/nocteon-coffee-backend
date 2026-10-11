package com.nocteon.nocteon_api.common.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Static checks over every repository query, written after a QA pass found the storefront's
 * filter dropdowns silently returning zero results on seven of nine endpoints.
 *
 * <p>Both bugs here are invisible to unit tests that mock repositories, and both shipped with a
 * green suite. They only surface as "the endpoint works, it just has nothing in it".
 */
class RepositoryQueryBindingTest {

    /** Every {@code @RestRepository}/{@code @Repository} interface in the application. */
    private static final List<Class<?>> REPOSITORIES = discoverRepositories();

    /** Named parameters as they appear in a JPQL/SQL {@code @Query}. */
    private static final Pattern NAMED_PARAM = Pattern.compile(":([a-zA-Z][a-zA-Z0-9_]*)");

    /**
     * Every named parameter a query binds must be resolvable to a method argument.
     *
     * <p>The defect: sixteen repositories imported {@code io.lettuce.core.dynamic.annotation.Param}
     * — Lettuce's Redis annotation — instead of Spring Data's. Spring Data does not read it, so
     * {@code @Param} became a no-op and binding silently fell back to positional. Seven services
     * happened to pass {@code (language, search)} against a method declaring
     * {@code (search, language)}, so {@code :search} was bound to the locale and {@code :language}
     * to the user's query text. Nothing failed; the filter simply matched nothing.
     *
     * <p>Positional binding is legal, so this asserts the stronger property that every named
     * parameter is <em>explicitly</em> annotated: then call order can never matter, and a wrong
     * import can no longer quietly reintroduce the failure.
     */
    @Test
    @DisplayName("every named parameter in a @Query is annotated with Spring Data's @Param")
    void everyNamedQueryParameterIsAnnotated() {
        List<String> unannotated = new ArrayList<>();

        for (Class<?> repository : REPOSITORIES) {
            for (Method method : repository.getDeclaredMethods()) {
                String query = queryOf(method);
                if (query == null) {
                    continue;
                }
                Set<String> named = namedParameters(query);

                for (Parameter parameter : method.getParameters()) {
                    if (parameter.getType().getSimpleName().equals("Pageable")
                            || parameter.getType().getSimpleName().equals("Sort")) {
                        continue;
                    }
                    Param param = parameter.getAnnotation(Param.class);
                    if (param == null || !named.contains(param.value())) {
                        unannotated.add(repository.getSimpleName() + "." + method.getName()
                                + " -> :" + (param == null ? parameter.getName() : param.value()));
                    }
                }
            }
        }

        assertThat(unannotated)
                .as("""
                        Named query parameters that no Spring Data @Param points at.

                        Without it, binding falls back to positional order — and a service that \
                        passes its arguments in a different order than the method declares binds \
                        the wrong value to each placeholder. That is not an error, it is a query \
                        that silently matches nothing.

                        This is the exact failure behind 16 repositories importing \
                        io.lettuce.core.dynamic.annotation.Param instead of \
                        org.springframework.data.repository.query.Param.""")
                .isEmpty();
    }

    /**
     * The "no search term supplied" guard must be null-safe.
     *
     * <p>The defect: {@code WHERE (:search = '' OR name LIKE ...)} evaluates {@code NULL = ''} when
     * the caller passes a null search term, which is three-valued logic: the predicate is
     * {@code NULL}, not {@code TRUE}, so the row is filtered out. Calling the endpoint with no
     * {@code search} parameter at all therefore returned an empty list even though the table had
     * rows — for {@code /api/roast-levels}, {@code /api/origins}, {@code /api/pairings} and four
     * more, all of which had been returning 401 beforehand, so nothing had exercised them.
     *
     * <p>{@code COALESCE(:search, '') = ''} is the fix; a bare {@code :search = ''} is the bug.
     */
    @Test
    @DisplayName("no query compares a search parameter to '' without a null guard")
    void searchParameterComparisonsAreNullSafe() {
        List<String> unsafe = new ArrayList<>();

        for (Class<?> repository : REPOSITORIES) {
            for (Method method : repository.getDeclaredMethods()) {
                String query = queryOf(method);
                if (query == null) {
                    continue;
                }
                for (String fragment : List.of(":search = ''", ":search=''", ":search = \"\"")) {
                    if (query.contains(fragment)) {
                        unsafe.add(repository.getSimpleName() + "." + method.getName()
                                + " -> " + fragment);
                    }
                }
            }
        }

        assertThat(unsafe)
                .as("""
                        Queries that treat a missing search term as ''.

                        In SQL, NULL = '' is NULL rather than TRUE, so the OR short-circuit never \
                        fires and the whole predicate filters the row out. An endpoint called \
                        without ?search then returns nothing.

                        Use COALESCE(:search, '') = '' instead.""")
                .isEmpty();
    }

    // --- helpers -----------------------------------------------------------------------------

    private static String queryOf(Method method) {
        Query annotation = method.getAnnotation(Query.class);
        // Covers both JPQL and nativeQuery — the placeholder syntax is the same in each.
        return annotation == null ? null : annotation.value();
    }

    private static Set<String> namedParameters(String query) {
        // Skip string literals and line comments so a ':' in either is not read as a parameter.
        String stripped = query
                .replaceAll("'(?:[^']|'')*'", "''")
                .replaceAll("--[^\\n]*", "");
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = NAMED_PARAM.matcher(stripped);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static List<Class<?>> discoverRepositories() {
        // Deliberately NOT ClassPathScanningCandidateComponentProvider: its candidate check
        // rejects interfaces, so it returns nothing at all for a Spring Data repository and the
        // assertions below would pass vacuously. Resolving the resources directly is the only
        // way to see them.
        var resolver = new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
        List<Class<?>> found = new ArrayList<>();
        try {
            var resources = resolver.getResources("classpath*:com/nocteon/nocteon_api/**/*Repository.class");
            for (var resource : resources) {
                String name = resource.getURL().getPath();
                String className = name
                        .replaceAll("^.*?/(com/nocteon/nocteon_api/)", "$1")
                        .replaceAll("\\.class$", "")
                        .replace('/', '.');
                Class<?> candidate;
                try {
                    candidate = Class.forName(className, false,
                            Thread.currentThread().getContextClassLoader());
                } catch (ClassNotFoundException e) {
                    continue;
                }
                if (candidate.isInterface()) {
                    found.add(candidate);
                }
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("could not scan for repositories", e);
        }
        found.sort(java.util.Comparator.comparing(Class::getName));
        return List.copyOf(found);
    }

    /**
     * Guards the guard.
     *
     * <p>Every assertion in this class iterates {@link #REPOSITORIES}. If discovery silently
     * returns nothing, all of them pass while checking nothing at all — which is precisely how the
     * first version of this test looked green while the bug was present. This pins the count from
     * the live classpath so an empty scan fails loudly.
     */
    @Test
    @DisplayName("repository discovery actually finds repositories")
    void repositoryDiscoveryIsNotEmpty() {
        assertThat(REPOSITORIES)
                .as("""
                        No repositories were discovered, so every other test in this class passes \
                        without checking anything.

                        Interfaces are not candidate components, so discovery must resolve \
                        *Repository.class resources directly.""")
                .hasSizeGreaterThanOrEqualTo(15)
                .allSatisfy(repository -> assertThat(repository.getSimpleName())
                        .endsWith("Repository"));

        assertThat(REPOSITORIES)
                .as("these repositories all use @Param and are the ones the bug shipped in")
                .anyMatch(repository -> repository.getSimpleName().equals("RoastLevelRepository"))
                .anyMatch(repository -> repository.getSimpleName().equals("CategoryRepository"));
    }
}