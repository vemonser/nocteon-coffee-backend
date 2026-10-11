package com.nocteon.nocteon_api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.nocteon.nocteon_api.auth.config.SecurityConfig;
import com.nocteon.nocteon_api.auth.enums.Role;
import com.nocteon.nocteon_api.common.enums.Permission;

/**
 * Structural checks over the authorisation configuration, written because every defect they cover
 * reached {@code main} with a green suite.
 *
 * <p>Each test corresponds to a real bug: an authority string with no Permission behind it (403 for
 * everyone, ADMIN included), a public endpoint whose actual path was missing from the permitAll
 * list (401 for the storefront), a catch-all mapping over the whole {@code /api} namespace, and a
 * dashboard surface gated on a permission customers hold.
 *
 * <p>The controllers are read from the compiled classes rather than from source text, so a test
 * here fails at build time instead of in a browser.
 */
class AuthorizationConfigTest {

    private static final Pattern AUTHORITY = Pattern.compile("hasAuthority\\('([^']+)'\\)");

    /**
     * Every {@code @RestController} in the application, discovered rather than listed.
     *
     * <p>A hardcoded list is the same mistake this class exists to catch: it goes stale silently,
     * and a controller added later is simply not checked. Scanning the package means a new endpoint
     * is covered the moment it is written.
     */
    private static final List<Class<?>> CONTROLLERS = discoverControllers();

    private static List<Class<?>> discoverControllers() {
        var scanner = new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(
                false);
        scanner.addIncludeFilter(
                new org.springframework.core.type.filter.AnnotationTypeFilter(
                        org.springframework.web.bind.annotation.RestController.class));
        List<Class<?>> found = new java.util.ArrayList<>();
        for (var descriptor : scanner.findCandidateComponents("com.nocteon.nocteon_api")) {
            try {
                found.add(Class.forName(descriptor.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        found.sort(java.util.Comparator.comparing(Class::getName));
        return List.copyOf(found);
    }

    /**
     * Every authority a controller demands must exist as a Permission constant.
     *
     * <p>{@code hasAuthority} compares raw strings, so a typo is neither a compile error nor a
     * startup error — it is a 403 that only appears when someone calls the endpoint. ADMIN cannot
     * reach it either, because ADMIN is granted {@code Permission.values()}.
     *
     * <p>Catches {@code product_option:read|create|update|delete} across seven endpoints and
     * {@code user:create} on {@code POST /api/dashboard/users}.
     */
    @Test
    @DisplayName("every authority named in @PreAuthorize exists as a Permission")
    void everyPreAuthorizeAuthorityIsARealPermission() {
        Set<String> declared = Arrays.stream(Permission.values())
                .map(Permission::getPermission)
                .collect(Collectors.toSet());

        List<String> unknown = CONTROLLERS.stream()
                .flatMap(controller -> Arrays.stream(controller.getDeclaredMethods()))
                .map(method -> method.getAnnotation(PreAuthorize.class))
                .filter(java.util.Objects::nonNull)
                .flatMap(preAuthorize -> authorities(preAuthorize.value()).stream())
                .filter(authority -> !declared.contains(authority))
                .distinct()
                .toList();

        assertThat(unknown)
                .as("""
                        @PreAuthorize names authorities that no Permission constant declares.

                        hasAuthority compares raw strings, so an unknown authority fails closed: the \
                        endpoint returns 403 for every role, and ADMIN cannot reach it either.

                        Fix by mapping the endpoint onto an existing permission, or by adding the \
                        constant and granting it to the roles that should hold it.""")
                .isEmpty();
    }

    /**
     * A staff-only surface must not be gated on a permission customers hold.
     *
     * <p>Catches {@code DashboardCartController} using {@code cart:manage}, which CUSTOMER holds
     * for its own basket, over {@code GET /api/dashboard/carts} — whose service reads by id with
     * no ownership check and returns another user's email, full name and basket contents.
     */
    @Test
    @DisplayName("the dashboard cart inbox is not gated on the customer basket permission")
    void dashboardCartInboxIsNotCustomerAccessible() {
        Set<Permission> customer = Role.CUSTOMER.getPermissions();
        String basketPermission = Permission.CART_MANAGE.getPermission();

        assertThat(customer)
                .as("precondition: CUSTOMER holds %s for its own basket, which is why it cannot also "
                        + "gate staff-wide visibility", basketPermission)
                .contains(Permission.CART_MANAGE);

        assertThat(customer)
                .as("%s must be staff-only", Permission.CART_READ_ALL.getPermission())
                .doesNotContain(Permission.CART_READ_ALL);

        List<String> authorities = CONTROLLERS.stream()
                .filter(controller -> controller
                        .getAnnotation(RequestMapping.class) != null
                        && controller.getAnnotation(RequestMapping.class).value()[0]
                                .equals("/api/dashboard/carts"))
                .flatMap(controller -> Arrays.stream(controller.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
                .flatMap(method -> authorities(
                        method.getAnnotation(PreAuthorize.class).value()).stream())
                .toList();

        assertThat(authorities)
                .as("the cart inbox exposes other users' carts, so it must not reuse the "
                        + "customer basket permission")
                .isNotEmpty()
                .doesNotContain(basketPermission);
    }

    /**
     * The storefront's read endpoints must actually be reachable without a token.
     *
     * <p>Catches {@code /api/roast-profiles} in the permitAll list against a controller mapping
     * {@code /api/roast-levels} (storefront got 401), and the public journal missing entirely.
     */
    @Test
    @DisplayName("public lookup endpoints are covered by the permitAll list")
    void publicLookupEndpointsArePermitted() {
        List<String> expectedPublic = List.of(
                "/api/categories",
                "/api/origins",
                "/api/farms",
                "/api/roast-levels",
                "/api/processing-methods",
                "/api/coffee-varieties",
                "/api/tasting-notes",
                "/api/brewing-methods",
                "/api/pairings",
                "/api/journal",
                "/api/products",
                "/api/settings");

        List<String> unreachable = expectedPublic.stream()
                .filter(path -> !isPermitted(path))
                .toList();

        assertThat(unreachable)
                .as("""
                        Public storefront endpoints that the permitAll list does not cover.

                        A permitAll entry only helps if it matches the path a controller actually \
                        maps. A mismatch returns 401 rather than anything useful, which reads as \
                        "the API is down".""")
                .isEmpty();
    }

    /**
     * The permitAll list must not name a path that no controller serves.
     *
     * <p>Catches {@code /api/roast-profiles}, which survived long after the controller was renamed
     * to {@code /api/roast-levels}: the stale entry looked correct, so it was never questioned,
     * while the real endpoint was blocked.
     */
    @Test
    @DisplayName("the permitAll list names only paths a controller actually serves")
    void permitAllListHasNoStaleEntries() {
        List<String> mapped = allMappedGetPaths();

        List<String> stale = Arrays.stream(SecurityConfig.PUBLIC_READ_PATHS)
                .filter(path -> !path.endsWith("/**"))
                .filter(path -> !mapped.contains(path))
                .toList();

        assertThat(stale)
                .as("""
                        permitAll entries that no controller maps.

                        These are harmless at runtime and misleading on review: they read as if the \
                        endpoint were public when the real path is not covered at all.""")
                .isEmpty();
    }

    /**
     * No public endpoint may claim the whole {@code /api} namespace.
     *
     * <p>Catches {@code RoastLevelController} mapping {@code @GetMapping("/{slug}")} under a
     * class-level {@code /api}: every unknown single-segment path resolved to a roast-level
     * lookup instead of 404, and the roast-level detail page had no route at all.
     */
    @Test
    @DisplayName("no endpoint maps a bare single-segment catch-all under /api")
    void noCatchAllOverTheApiNamespace() {
        List<String> catchAlls = CONTROLLERS.stream()
                .filter(controller -> controller.getAnnotation(RequestMapping.class) != null
                        && controller.getAnnotation(RequestMapping.class).value()[0].equals("/api"))
                .flatMap(controller -> Arrays.stream(controller.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .flatMap(method -> Arrays.stream(method.getAnnotation(GetMapping.class).value()))
                .filter(path -> path.startsWith("/{"))
                .toList();

        assertThat(catchAlls)
                .as("a @GetMapping(\"/{x}\") under a class-level @RequestMapping(\"/api\") claims every "
                        + "single-segment /api path, so unknown routes stop returning 404 and start "
                        + "hitting a roast-level lookup. Qualify it, e.g. /roast-levels/{slug}.")
                .isEmpty();
    }

    /**
     * A dashboard handler must not contradict its own class-level mapping.
     *
     * <p>Catches {@code ContactController}: class-level {@code /api/contact} plus a method mapping
     * of {@code /api/dashboard/contact-messages} compose to
     * {@code /api/contact/api/dashboard/contact-messages}. The dashboard inbox 404'd, and because
     * the composed path no longer began with {@code /api/dashboard/} it also escaped the dashboard
     * security rule.
     */
    @Test
    @DisplayName("no handler composes a path that contradicts its class mapping")
    void noHandlerContradictsItsClassMapping() {
        List<String> contradictions = CONTROLLERS.stream()
                .filter(controller -> controller.getAnnotation(RequestMapping.class) != null
                        && controller.getAnnotation(RequestMapping.class).value()[0].startsWith("/api"))
                .flatMap(controller -> Arrays.stream(controller.getDeclaredMethods())
                        .flatMap(method -> mappings(method).stream()
                                .filter(path -> path.startsWith("/api"))
                                .map(path -> controller.getSimpleName() + "." + method.getName()
                                        + " -> " + path)))
                .distinct()
                .toList();

        assertThat(contradictions)
                .as("a method mapping starting with /api under a class mapping that also starts "
                        + "with /api is concatenated, producing a path no client would guess")
                .isEmpty();
    }

    // --- helpers -----------------------------------------------------------------------------

    private static List<String> authorities(String expression) {
        Matcher matcher = AUTHORITY.matcher(expression);
        List<String> found = new java.util.ArrayList<>();
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static List<String> mappings(java.lang.reflect.Method method) {
        List<String> paths = new java.util.ArrayList<>();
        // Stream.of, not List.of: a method carries exactly one of these, so the others are null and
        // List.of rejects null elements outright.
        var candidates = java.util.stream.Stream.of(
                method.getAnnotation(GetMapping.class),
                method.getAnnotation(PostMapping.class),
                method.getAnnotation(PutMapping.class),
                method.getAnnotation(DeleteMapping.class),
                method.getAnnotation(PatchMapping.class));
        candidates.filter(java.util.Objects::nonNull)
                .flatMap(annotation -> Arrays.stream(annotationPaths(annotation)))
                .forEach(paths::add);
        return paths;
    }

    private static String[] annotationPaths(java.lang.annotation.Annotation annotation) {
        if (annotation instanceof GetMapping get) {
            return get.value();
        }
        if (annotation instanceof PostMapping post) {
            return post.value();
        }
        if (annotation instanceof PutMapping put) {
            return put.value();
        }
        if (annotation instanceof DeleteMapping delete) {
            return delete.value();
        }
        return ((PatchMapping) annotation).value();
    }

    private static List<String> allMappedGetPaths() {
        List<String> paths = new java.util.ArrayList<>();
        CONTROLLERS.forEach(controller -> {
            String prefix = controller.getAnnotation(RequestMapping.class) != null
                    && controller.getAnnotation(RequestMapping.class).value().length > 0
                            ? controller.getAnnotation(RequestMapping.class).value()[0]
                            : "";
            Arrays.stream(controller.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(GetMapping.class))
                    .forEach(method -> Arrays
                            .stream(method.getAnnotation(GetMapping.class).value())
                            .forEach(suffix -> paths.add(
                                    suffix.isEmpty() ? prefix : prefix + suffix)));
        });
        return paths;
    }

    private static boolean isPermitted(String path) {
        return Arrays.stream(SecurityConfig.PUBLIC_READ_PATHS)
                .anyMatch(pattern -> matches(pattern, path));
    }

    private static boolean matches(String antPattern, String path) {
        if (antPattern.endsWith("/**")) {
            String prefix = antPattern.substring(0, antPattern.length() - 3);
            return path.equals(prefix) || path.startsWith(prefix + "/");
        }
        return antPattern.equals(path);
    }
}