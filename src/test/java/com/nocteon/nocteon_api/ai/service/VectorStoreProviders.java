package com.nocteon.nocteon_api.ai.service;

import java.util.Map;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/**
 * {@link ObjectProvider} fixtures for the AI services, which take the vector store as an optional
 * collaborator.
 *
 * <p>A real {@link StaticListableBeanFactory} rather than a mocked {@code ObjectProvider}: the
 * services depend on {@code getIfAvailable()} answering "the bean" or "null", and that is Spring
 * behaviour worth exercising rather than restating. Mocking the interface would let a test pass
 * with a stubbed answer the container would never give.
 *
 * <p>{@link #absent()} is the keyword-only deployment: no embedding provider, so no store bean.
 */
final class VectorStoreProviders {

    private VectorStoreProviders() {
    }

    /** A provider that resolves to {@code store}. */
    static ObjectProvider<VectorStore> of(VectorStore store) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory(Map.<String, Object>of("vectorStore", store));
        return factory.getBeanProvider(VectorStore.class);
    }

    /** A provider over an empty context: {@code getIfAvailable()} answers {@code null}. */
    static ObjectProvider<VectorStore> absent() {
        return new StaticListableBeanFactory().getBeanProvider(VectorStore.class);
    }
}
