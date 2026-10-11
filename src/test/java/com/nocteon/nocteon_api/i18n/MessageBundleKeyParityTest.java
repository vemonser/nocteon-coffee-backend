package com.nocteon.nocteon_api.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two message bundles must expose the same key set.
 *
 * <h2>Why this fails the build instead of shipping</h2>
 * {@code messages_ar} is a child bundle of {@code messages}, so a key present only in the parent
 * resolves silently: the lookup walks up to English and returns it. The Arabic storefront then
 * renders an English string — no exception, no log line, no failing request, just text in the wrong
 * language in the one place a customer reads it. The same is true of a key present only in
 * {@code messages_ar}, which English then shows as a raw {@code some.key} because there is no
 * parent to fall back to.
 *
 * <p>Neither failure is visible in any other test in this suite: {@code GlobalExceptionHandler}
 * resolves a missing key to a usable message either way, so a controller test passes with the
 * English string where the Arabic one should have been. This is the only place the divergence can
 * be caught, which is what makes it worth a test.
 *
 * <p>The real bundles on the classpath are read directly rather than a fixture, because a fixture
 * would only ever prove that the fixture is self-consistent. {@code src/main/resources} is on the
 * test classpath, so these are the same bytes the running application loads.
 */
class MessageBundleKeyParityTest {

    private static final String BASE_BUNDLE = "messages.properties";
    private static final String ARABIC_BUNDLE = "messages_ar.properties";

    @Test
    void theArabicBundleDeclaresEveryKeyTheBaseBundleDoes() {
        assertThat(keysOf(ARABIC_BUNDLE))
                .as("keys present in %s but missing from %s fall back to English on the Arabic storefront",
                        BASE_BUNDLE, ARABIC_BUNDLE)
                .containsAll(keysOf(BASE_BUNDLE));
    }

    @Test
    void theBaseBundleDeclaresEveryKeyTheArabicBundleDoes() {
        assertThat(keysOf(BASE_BUNDLE))
                .as("keys present in %s but missing from %s render as a raw key in English, since there is no parent bundle to fall back to",
                        ARABIC_BUNDLE, BASE_BUNDLE)
                .containsAll(keysOf(ARABIC_BUNDLE));
    }

    /** Both directions, as one diff, so a divergence is reported as a single readable failure. */
    @Test
    void theTwoBundlesExposeExactlyTheSameKeys() {
        Set<String> base = keysOf(BASE_BUNDLE);
        Set<String> arabic = keysOf(ARABIC_BUNDLE);

        Set<String> onlyInBase = new TreeSet<>(base);
        onlyInBase.removeAll(arabic);
        Set<String> onlyInArabic = new TreeSet<>(arabic);
        onlyInArabic.removeAll(base);

        assertThat(onlyInBase).as("only in %s (silently falls back to English in Arabic)", BASE_BUNDLE).isEmpty();
        assertThat(onlyInArabic).as("only in %s (renders as a raw key in English)", ARABIC_BUNDLE).isEmpty();
    }

    /** Guards against a bundle that parses to nothing and so passes both parity tests vacuously. */
    @Test
    void bothBundlesAreNonEmpty() {
        assertThat(keysOf(BASE_BUNDLE)).as("%s must not be empty", BASE_BUNDLE).isNotEmpty();
        assertThat(keysOf(ARABIC_BUNDLE)).as("%s must not be empty", ARABIC_BUNDLE).isNotEmpty();
    }

    private static Set<String> keysOf(String bundle) {
        Properties properties = load(bundle);

        return properties.stringPropertyNames().stream()
                .map(String::trim)
                .filter(key -> !key.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Properties load(String bundle) {
        Resource resource = new ClassPathResource(bundle);
        assertThat(resource.exists()).as("%s must be on the test classpath", bundle).isTrue();

        Properties properties = new Properties();
        // Properties.load(Reader) reads characters literally rather than as ISO-8859-1 bytes, which
        // is what makes the Arabic values legible — irrelevant to key parity, but it means this
        // parses the same bytes Spring's message source does.
        try (InputStream stream = resource.getInputStream();
                Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read " + bundle, e);
        }

        return properties;
    }
}