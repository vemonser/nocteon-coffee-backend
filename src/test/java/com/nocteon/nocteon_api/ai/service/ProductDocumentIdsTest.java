package com.nocteon.nocteon_api.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Freezes the document-id derivation as a persisted-data contract.
 *
 * <p>The derivation is not an implementation detail: the value is the primary key of every row in
 * the live {@code vector_store} table. Every other test in this package proves the derivation is
 * internally <em>consistent</em> — the same function on both sides of the assertion — which is
 * exactly why a change to the prefix or the charset would pass the suite while orphaning every
 * indexed row and inserting a duplicate per product on the next re-index. {@link #idIsFrozenForKnownSlugs()}
 * is the only test that pins the literal output, and it is the analogue of a migration checksum.
 */
class ProductDocumentIdsTest {

    /**
     * The frozen contract. Changing any of these literals requires a full re-index and must be
     * done in the same commit that changes the derivation.
     *
     * <p>Reproduce with: {@code UUID.nameUUIDFromBytes(("product:" + slug).getBytes(UTF_8))}
     */
    @Test
    @DisplayName("id is frozen for known slugs")
    void idIsFrozenForKnownSlugs() {
        assertThat(ProductDocumentIds.forSlug("ethiopia-yirgacheffe"))
                .isEqualTo("edafab23-43fc-37c9-b32d-f5d9fc83337d");
        assertThat(ProductDocumentIds.forSlug("colombia-huila"))
                .isEqualTo("6a3670b7-6b9a-35ae-b5e3-f1144e5f0b43");
        assertThat(ProductDocumentIds.forSlug("hario-v60"))
                .isEqualTo("b6024ded-c7a8-3390-879b-affa46917b8c");
    }

    @Test
    @DisplayName("the store's own parser accepts every id we generate")
    void idsAreAcceptedByTheRealStore() {
        // The defect that started all of this: PgVectorStore runs UUID.fromString on each id, so a
        // non-UUID string fails on the first real write even though every mocked test passed.
        for (String slug : new String[] { "ethiopia-yirgacheffe", "قهوة-إثيوبية", "a", "x".repeat(150) }) {
            assertThat(UUID.fromString(ProductDocumentIds.forSlug(slug)))
                    .as("PgVectorStore must accept the id for \"%s\"", slug)
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("the same slug always yields the same id")
    void deterministic() {
        assertThat(ProductDocumentIds.forSlug("ethiopia-yirgacheffe"))
                .isEqualTo(ProductDocumentIds.forSlug("ethiopia-yirgacheffe"))
                .isEqualTo(ProductDocumentIds.forSlug("ethiopia-yirgacheffe"));
    }

    @Test
    @DisplayName("distinct slugs yield distinct ids")
    void distinct() {
        assertThat(ProductDocumentIds.forSlug("ethiopia-yirgacheffe"))
                .isNotEqualTo(ProductDocumentIds.forSlug("ethiopia-yirgacheffe-2"))
                .isNotEqualTo(ProductDocumentIds.forSlug("Ethiopia-Yirgacheffe"));
    }

    @Test
    @DisplayName("the prefix prevents a cross-namespace collision")
    void namespaceSeparated() {
        // A hypothetical category document for the same slug must not land on the product's row.
        String productId = ProductDocumentIds.forSlug("ethiopia-yirgacheffe");
        String categoryId = UUID.nameUUIDFromBytes(
                        ("category:" + "ethiopia-yirgacheffe").getBytes(StandardCharsets.UTF_8))
                .toString();
        assertThat(productId).isNotEqualTo(categoryId);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "\t", "\n" })
    @DisplayName("a blank slug is rejected rather than collapsing onto one shared id")
    void blankSlugRejected(String slug) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProductDocumentIds.forSlug(slug))
                .withMessageContaining("non-blank");
    }

    @Test
    @DisplayName("a null slug is rejected rather than hashing the literal \"product:null\"")
    void nullSlugRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> ProductDocumentIds.forSlug(null));
    }

    @Test
    @DisplayName("the id is a version 3 (name-based) UUID")
    void versionThree() {
        assertThat(UUID.fromString(ProductDocumentIds.forSlug("anything")).version()).isEqualTo(3);
    }
}