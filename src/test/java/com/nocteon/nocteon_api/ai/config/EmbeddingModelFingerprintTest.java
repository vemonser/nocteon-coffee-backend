package com.nocteon.nocteon_api.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * The fingerprint mixed into every indexed document's {@code contentHash}.
 *
 * <p>Its whole purpose is to change exactly when the configured embedding model changes, so the
 * tests are about when it changes and when it must not. "The model" here is three things — the
 * provider, the model id (with its vendor, for the generic OpenAI-compatible provider) and the
 * output width — and each of them is pinned separately below, because each of them alone is enough
 * to move the catalogue into a different embedding space.
 */
class EmbeddingModelFingerprintTest {

    private static final String DECISION = EmbeddingProviderEnvironmentPostProcessor.PROVIDER_DECISION_PROPERTY;
    private static final String OLLAMA = EmbeddingProviderEnvironmentPostProcessor.EMBEDDING_SELECTOR_PROPERTY;
    private static final String TEXT = EmbeddingProviderEnvironmentPostProcessor.TEXT_EMBEDDING_SELECTOR_PROPERTY;

    private static MockEnvironment geminiEnvironment() {
        return new MockEnvironment()
                .withProperty(DECISION, EmbeddingProviderEnvironmentPostProcessor.GOOGLE_GENAI_PROVIDER)
                .withProperty(TEXT, EmbeddingProviderEnvironmentPostProcessor.GOOGLE_GENAI_PROVIDER)
                .withProperty(EmbeddingProviderEnvironmentPostProcessor.EMBEDDING_SELECTOR_PROPERTY,
                        EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER)
                .withProperty("spring.ai.google.genai.embedding.text.model", "gemini-embedding-2")
                .withProperty("spring.ai.google.genai.embedding.text.dimensions", "768")
                .withProperty("spring.ai.vectorstore.pgvector.dimensions", "768");
    }

    private static MockEnvironment ollamaEnvironment() {
        return new MockEnvironment()
                .withProperty(DECISION, EmbeddingProviderEnvironmentPostProcessor.OLLAMA_PROVIDER)
                .withProperty(OLLAMA, EmbeddingProviderEnvironmentPostProcessor.OLLAMA_PROVIDER)
                .withProperty(TEXT, EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER)
                .withProperty("spring.ai.ollama.embedding.options.model", "nomic-embed-text")
                .withProperty("spring.ai.vectorstore.pgvector.dimensions", "768");
    }

    private static MockEnvironment openAiCompatibleEnvironment() {
        return new MockEnvironment()
                .withProperty(DECISION, EmbeddingProviderEnvironmentPostProcessor.OPENAI_PROVIDER)
                .withProperty(OLLAMA, EmbeddingProviderEnvironmentPostProcessor.OPENAI_PROVIDER)
                .withProperty(TEXT, EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER)
                .withProperty("spring.ai.openai.embedding.model", "text-embedding-3-small")
                .withProperty("spring.ai.openai.embedding.dimensions", "768")
                .withProperty("spring.ai.openai.embedding.base-url", "https://api.openai.com/v1")
                .withProperty("spring.ai.vectorstore.pgvector.dimensions", "768");
    }

    private static MockEnvironment keywordOnlyEnvironment() {
        return new MockEnvironment()
                .withProperty(DECISION, EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER)
                .withProperty(OLLAMA, EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER)
                .withProperty(TEXT, EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER)
                .withProperty("spring.ai.vectorstore.pgvector.dimensions", "768");
    }

    private static String fingerprint(MockEnvironment environment) {
        return new EmbeddingModelFingerprint(environment).value();
    }

    // --- the case this class exists for -------------------------------------------------

    /**
     * Both hosted providers can emit 768 dimensions, so the width is identical; only the provider
     * and the model name differ. If the fingerprint did not change here, every stored vector would
     * survive a provider swap and the index would silently return wrong results.
     */
    @Test
    void changesWhenTheProviderChanges() {
        String gemini = fingerprint(geminiEnvironment());
        String ollama = fingerprint(ollamaEnvironment());
        String hosted = fingerprint(openAiCompatibleEnvironment());

        assertThat(gemini).isNotEqualTo(ollama);
        assertThat(gemini).isNotEqualTo(hosted);
        assertThat(ollama).isNotEqualTo(hosted);
    }

    /** The provider id is in the value, not merely inferable from the model name. */
    @Test
    void namesTheProviderAndModelItWasBuiltFrom() {
        assertThat(fingerprint(geminiEnvironment())).startsWith("google-genai:gemini-embedding-2:");
        assertThat(fingerprint(ollamaEnvironment())).startsWith("ollama:nomic-embed-text:");
        assertThat(fingerprint(openAiCompatibleEnvironment())).startsWith("openai:text-embedding-3-small@api.openai.com:");
    }

    /** A second Gemini embedding model at the same width is a different space. */
    @Test
    void changesWhenOnlyTheModelNameChanges() {
        String first = fingerprint(geminiEnvironment());
        String second = fingerprint(geminiEnvironment()
                .withProperty("spring.ai.google.genai.embedding.text.model", "gemini-embedding-001"));

        assertThat(first).isNotEqualTo(second);
    }

    /**
     * For the generic provider the model id alone is not the identity: the same id served by two
     * vendors is two services, and re-pointing {@code EMBEDDING_BASE_URL} must re-embed the
     * catalogue rather than blend two vendors' vectors in one table.
     */
    @Test
    void changesWhenTheGenericProviderPointsAtAnotherVendor() {
        String openAi = fingerprint(openAiCompatibleEnvironment());
        String voyage = fingerprint(openAiCompatibleEnvironment()
                .withProperty("spring.ai.openai.embedding.base-url", "https://api.voyageai.com/v1"));

        assertThat(openAi).isNotEqualTo(voyage);
        assertThat(voyage).contains("api.voyageai.com");
    }

    /**
     * The endpoint's path and key are not fingerprinted — the host identifies the vendor, and the
     * key must never end up in a hash that is written to the database.
     */
    @Test
    void ignoresTheEndpointPathAndNeverTheApiKey() {
        String first = fingerprint(openAiCompatibleEnvironment());
        String second = fingerprint(openAiCompatibleEnvironment()
                .withProperty("spring.ai.openai.embedding.base-url", "https://api.openai.com/v2")
                .withProperty("spring.ai.openai.embedding.api-key", "sk-should-not-appear"));

        assertThat(second).isEqualTo(first);
        assertThat(second).doesNotContain("sk-");
    }

    /** An unchanged configuration must produce an identical fingerprint, or every reindex re-embeds. */
    @Test
    void isStableForAnUnchangedConfiguration() {
        assertThat(fingerprint(geminiEnvironment())).isEqualTo(fingerprint(geminiEnvironment()));
        assertThat(fingerprint(ollamaEnvironment())).isEqualTo(fingerprint(ollamaEnvironment()));
        assertThat(fingerprint(openAiCompatibleEnvironment())).isEqualTo(fingerprint(openAiCompatibleEnvironment()));
        assertThat(fingerprint(keywordOnlyEnvironment())).isEqualTo(fingerprint(keywordOnlyEnvironment()));
    }

    // --- dimensions ---------------------------------------------------------------------

    /**
     * A requested-width change moves the vectors into another space. It is written
     * {@code <requested>@<column>} so both halves are visible and either one moving changes the
     * hash — the request because the vectors differ, the column because it is the schema the
     * writes have to satisfy.
     */
    @Test
    void changesWhenTheRequestedWidthChanges() {
        String width768 = fingerprint(geminiEnvironment());
        String width1536 = fingerprint(geminiEnvironment()
                .withProperty("spring.ai.google.genai.embedding.text.dimensions", "1536"));

        assertThat(width768).endsWith(":768@768");
        assertThat(width1536).endsWith(":1536@768");
        assertThat(width1536).isNotEqualTo(width768);
    }

    /** And a column change is a different fingerprint even when the request is unchanged. */
    @Test
    void changesWhenTheVectorColumnWidthChanges() {
        String column768 = fingerprint(geminiEnvironment());
        String column1536 = fingerprint(geminiEnvironment()
                .withProperty("spring.ai.vectorstore.pgvector.dimensions", "1536"));

        assertThat(column768).endsWith(":768@768");
        assertThat(column1536).endsWith(":768@1536");
        assertThat(column1536).isNotEqualTo(column768);
    }

    /**
     * A local model has no request width to read, so the column is the only width there is.
     */
    @Test
    void aLocalModelFingerprintsTheColumnWidthOnBothSides() {
        assertThat(fingerprint(ollamaEnvironment())).endsWith(":768@768");
    }

    /** An unparseable width must not throw at startup — a degraded fingerprint is better than none. */
    @Test
    void anUnparseableWidthDegradesToZero() {
        assertThat(fingerprint(geminiEnvironment()
                .withProperty("spring.ai.vectorstore.pgvector.dimensions", "not-a-number")))
                .endsWith(":768@0");
    }

    // --- keyword-only -------------------------------------------------------------------

    /**
     * Keyword-only search is a mode like any other, and it must not share a fingerprint with a
     * real model: if it did, switching a provider on would look like "nothing changed" and the
     * catalogue would never be embedded.
     */
    @Test
    void keywordOnlySearchHasItsOwnFingerprint() {
        String keywordOnly = fingerprint(keywordOnlyEnvironment());

        assertThat(keywordOnly).startsWith("none:keyword-only:");
        assertThat(keywordOnly).isNotEqualTo(fingerprint(geminiEnvironment()));
        assertThat(keywordOnly).isNotEqualTo(fingerprint(ollamaEnvironment()));
    }

    // --- constructors and fallbacks -----------------------------------------------------

    /** A blank model name still yields a usable value; an empty string would hash the same for everything. */
    @Test
    void aBlankModelNameBecomesAPlaceholderRatherThanAnEmptyFingerprint() {
        assertThat(new EmbeddingModelFingerprint("ollama", "  ", 768).value())
                .isEqualTo("ollama:unknown:768@768");
    }

    /** A missing width property is read the same way the store's own default would be. */
    @Test
    void aMissingWidthPropertyFallsBackToZero() {
        assertThat(new EmbeddingModelFingerprint("google-genai", "gemini-embedding-2", 0).value())
                .isEqualTo("google-genai:gemini-embedding-2:0@0");
    }

    /**
     * Without the decision property the provider is read off the selectors, which is how a context
     * that never ran the post-processor — every unit test in this package — is fingerprinted.
     */
    @Test
    void derivesTheProviderFromTheSelectorsWhenNoDecisionWasPublished() {
        MockEnvironment bare = new MockEnvironment()
                .withProperty(OLLAMA, EmbeddingProviderEnvironmentPostProcessor.OLLAMA_PROVIDER)
                .withProperty("spring.ai.ollama.embedding.options.model", "nomic-embed-text");

        assertThat(fingerprint(bare)).isEqualTo("ollama:nomic-embed-text:0@0");
    }

    /** With no selectors resolved either, the answer is the conservative one: no index. */
    @Test
    void defaultsToKeywordOnlyWhenNothingIsConfigured() {
        assertThat(fingerprint(new MockEnvironment())).startsWith("none:keyword-only:");
    }

    /** The selector the provider was chosen through decides which model name is read. */
    @Test
    void readsTheModelNameOfTheSelectedProviderOnly() {
        MockEnvironment both = geminiEnvironment()
                .withProperty("spring.ai.ollama.embedding.options.model", "nomic-embed-text");

        assertThat(fingerprint(both)).doesNotContain("nomic-embed-text");
    }

    /** The decision property wins over the selectors, so the two can never disagree. */
    @Test
    void thePublishedDecisionWinsOverTheSelectors() {
        MockEnvironment conflicting = geminiEnvironment()
                .withProperty(DECISION, EmbeddingProviderEnvironmentPostProcessor.OLLAMA_PROVIDER)
                .withProperty("spring.ai.ollama.embedding.options.model", "nomic-embed-text");

        assertThat(fingerprint(conflicting)).startsWith("ollama:nomic-embed-text:");
    }

    /** A blank decision property is not a decision; the selectors are still consulted. */
    @Test
    void aBlankDecisionPropertyIsIgnored() {
        MockEnvironment blank = geminiEnvironment().withProperty(DECISION, "  ");

        assertThat(fingerprint(blank)).startsWith("google-genai:");
    }

    /** Fingerprints are used in log lines and error messages, so {@code toString} must be the value. */
    @Test
    void toStringIsTheValue() {
        EmbeddingModelFingerprint fingerprint = new EmbeddingModelFingerprint(geminiEnvironment());

        assertThat(fingerprint.toString()).isEqualTo(fingerprint.value());
    }

    /** A malformed base URL degrades to the bare model id rather than failing the bean. */
    @Test
    void aMalformedBaseUrlDegradesToTheBareModelId() {
        assertThat(fingerprint(openAiCompatibleEnvironment()
                .withProperty("spring.ai.openai.embedding.base-url", "not a url")))
                .startsWith("openai:text-embedding-3-small:");
    }
}
