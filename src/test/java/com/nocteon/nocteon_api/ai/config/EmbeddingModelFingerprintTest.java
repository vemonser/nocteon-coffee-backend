package com.nocteon.nocteon_api.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * The fingerprint mixed into every indexed document's {@code contentHash}.
 *
 * <p>Its whole purpose is to change exactly when the configured embedding model changes, so the
 * tests are about when it changes and when it must not.
 */
class EmbeddingModelFingerprintTest {

    private static final String OLLAMA = EmbeddingProviderEnvironmentPostProcessor.EMBEDDING_SELECTOR_PROPERTY;
    private static final String TEXT = EmbeddingProviderEnvironmentPostProcessor.TEXT_EMBEDDING_SELECTOR_PROPERTY;

    private static MockEnvironment geminiEnvironment() {
        return new MockEnvironment()
                .withProperty(TEXT, EmbeddingProviderEnvironmentPostProcessor.GOOGLE_GENAI_PROVIDER)
                .withProperty(EmbeddingProviderEnvironmentPostProcessor.EMBEDDING_SELECTOR_PROPERTY,
                        EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER)
                .withProperty("spring.ai.google.genai.embedding.text.model", "gemini-embedding-2")
                .withProperty("spring.ai.google.genai.embedding.text.dimensions", "768")
                .withProperty("spring.ai.vectorstore.pgvector.dimensions", "768");
    }

    private static MockEnvironment ollamaEnvironment() {
        return new MockEnvironment()
                .withProperty(OLLAMA, EmbeddingProviderEnvironmentPostProcessor.OLLAMA_PROVIDER)
                .withProperty(TEXT, EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER)
                .withProperty("spring.ai.ollama.embedding.options.model", "nomic-embed-text")
                .withProperty("spring.ai.vectorstore.pgvector.dimensions", "768");
    }

    /**
     * The case this class exists for. Both providers emit 768 dimensions, so the width is
     * identical; only the model name differs. If the fingerprint did not change here, every stored
     * vector would survive a model swap and the index would silently return wrong results.
     */
    @Test
    void changesWhenTheEmbeddingModelChanges() {
        String gemini = new EmbeddingModelFingerprint(geminiEnvironment()).value();
        String ollama = new EmbeddingModelFingerprint(ollamaEnvironment()).value();

        assertThat(gemini).isNotEqualTo(ollama);
        assertThat(gemini).contains("gemini-embedding-2");
        assertThat(ollama).contains("nomic-embed-text");
    }

    /**
     * A second Gemini embedding model at the same width is still a different embedding space, so
     * the model name has to be part of the fingerprint — not just the dimension.
     */
    @Test
    void changesWhenOnlyTheModelNameChanges() {
        String first = new EmbeddingModelFingerprint(geminiEnvironment()).value();
        String second = new EmbeddingModelFingerprint(geminiEnvironment()
                .withProperty("spring.ai.google.genai.embedding.text.model", "gemini-embedding-001")).value();

        assertThat(first).isNotEqualTo(second);
    }

    /**
     * And the reverse: an unchanged configuration must produce an identical fingerprint, or every
     * product would be re-embedded on every single reindex run.
     */
    @Test
    void isStableForAnUnchangedConfiguration() {
        assertThat(new EmbeddingModelFingerprint(geminiEnvironment()).value())
                .isEqualTo(new EmbeddingModelFingerprint(geminiEnvironment()).value());
        assertThat(new EmbeddingModelFingerprint(ollamaEnvironment()).value())
                .isEqualTo(new EmbeddingModelFingerprint(ollamaEnvironment()).value());
    }

    /**
     * A dimension change has to move the fingerprint too. It would also break every insert against
     * the schema, but a hash that did not notice it would report the catalog as fresh while the
     * writes were failing.
     */
    @Test
    void changesWhenTheOutputWidthChanges() {
        MockEnvironment wider = geminiEnvironment()
                .withProperty("spring.ai.vectorstore.pgvector.dimensions", "1536");

        assertThat(new EmbeddingModelFingerprint(wider).value())
                .isNotEqualTo(new EmbeddingModelFingerprint(geminiEnvironment()).value());
    }

    /**
     * The dimension is read from the store's configuration rather than the provider's default,
     * because the store's value is the one the table was built for — so a provider default and a
     * configured width disagreeing cannot leave the fingerprint describing a model the schema does
     * not accept.
     */
    @Test
    void readsTheWidthFromTheVectorStoreNotTheProviderDefault() {
        MockEnvironment providerSays1536 = geminiEnvironment()
                .withProperty("spring.ai.google.genai.embedding.text.dimensions", "1536");

        assertThat(new EmbeddingModelFingerprint(providerSays1536).value())
                .endsWith(":768");
    }

    /** A blank model name still yields a usable value; an empty string would hash to the same key for everything. */
    @Test
    void aBlankModelNameBecomesAPlaceholderRatherThanAnEmptyFingerprint() {
        assertThat(new EmbeddingModelFingerprint("ollama", "  ", 768).value())
                .isEqualTo("ollama:unknown:768");
    }

    /** An unparseable width must not throw at startup — a degraded fingerprint is better than none. */
    @Test
    void anUnparseableWidthDegradesToZero() {
        assertThat(new EmbeddingModelFingerprint(geminiEnvironment()
                .withProperty("spring.ai.vectorstore.pgvector.dimensions", "not-a-number")).value())
                .endsWith(":0");
    }

    /** A missing width property is read the same way the store's own default would be. */
    @Test
    void aMissingWidthPropertyFallsBackToZero() {
        assertThat(new EmbeddingModelFingerprint("google-genai", "gemini-embedding-2", 0).value())
                .isEqualTo("google-genai:gemini-embedding-2:0");
    }

    /**
     * The selector the provider was chosen through decides which model name is read. Reading the
     * wrong one would produce a fingerprint naming a model that is not in use.
     */
    @Test
    void readsTheModelNameOfTheSelectedProviderOnly() {
        MockEnvironment both = geminiEnvironment()
                .withProperty("spring.ai.ollama.embedding.options.model", "nomic-embed-text");

        assertThat(new EmbeddingModelFingerprint(both).value())
                .doesNotContain("nomic-embed-text");
    }

    /**
     * A machine with no selectors resolved at all still fingerprints as the local model rather
     * than throwing — the post-processor may not have run in the reader's context.
     */
    @Test
    void defaultsToTheLocalProviderWhenNoSelectorIsSet() {
        MockEnvironment bare = new MockEnvironment()
                .withProperty("spring.ai.ollama.embedding.options.model", "nomic-embed-text");

        assertThat(new EmbeddingModelFingerprint(bare).value())
                .isEqualTo("ollama:nomic-embed-text:0");
    }
}