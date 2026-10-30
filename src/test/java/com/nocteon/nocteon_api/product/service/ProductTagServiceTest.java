package com.nocteon.nocteon_api.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nocteon.nocteon_api.common.exception.invalid.EntityInUseException;
import com.nocteon.nocteon_api.common.exception.notFound.ProductTagNotFoundException;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.common.service.TrashRules;
import com.nocteon.nocteon_api.common.service.TrashRules.Reference;
import com.nocteon.nocteon_api.product.dto.request.ProductTagRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductTagTranslationRequest;
import com.nocteon.nocteon_api.product.dto.response.ProductTagResponseDashboard;
import com.nocteon.nocteon_api.product.entity.ProductTag;
import com.nocteon.nocteon_api.product.entity.ProductTagTranslation;
import com.nocteon.nocteon_api.product.repository.ProductTagRepository;

/**
 * FE-10: the tag rules the admin page depends on.
 *
 * <p>Three of them are silent when they break: the slug is generated once (a rename must not
 * break the public filter that already points at it), the slug stays unique across trashed rows
 * (the column is unique there too), and a tag still carried by products is refused with the count
 * the page shows instead of an empty success.
 */
@ExtendWith(MockitoExtension.class)
class ProductTagServiceTest {

    @Mock
    private ProductTagRepository tagRepository;

    private LookupServiceHelper helper;
    private TrashRules trashRules;
    private ProductTagService service;

    @BeforeEach
    void setUp() {
        // Real slug arithmetic and real translation validation; only the advisory lock is stubbed
        // (it needs a database). TrashRules is stubbed too: the reference counts are SQL.
        helper = mock(LookupServiceHelper.class, CALLS_REAL_METHODS);
        trashRules = mock(TrashRules.class);
        service = new ProductTagService(tagRepository, helper,
                mock(com.nocteon.nocteon_api.audit.service.DomainAuditRecorder.class), trashRules);
    }

    private void stubNameLock() {
        doNothing().when(helper).acquireNameLock(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("creating a tag derives the slug from the English name, once")
    void createsTheSlugFromTheEnglishName() {
        stubNameLock();
        when(tagRepository.existsBySlugIncludingDeleted(anyString())).thenReturn(false);

        ProductTagResponseDashboard created = service.createTag(request("New arrivals", "وصل حديثاً"));

        ArgumentCaptor<ProductTag> saved = ArgumentCaptor.forClass(ProductTag.class);
        verify(tagRepository).save(saved.capture());
        assertThat(created.getSlug()).isEqualTo("new-arrivals");
        assertThat(saved.getValue().getTranslations())
                .extracting(ProductTagTranslation::getName)
                .containsExactlyInAnyOrder("New arrivals", "وصل حديثاً");
    }

    @Test
    @DisplayName("a slug already taken by a trashed tag gets the next free one")
    void neverReusesASlugEvenFromATrashedRow() {
        stubNameLock();
        when(tagRepository.existsBySlugIncludingDeleted("new-arrivals")).thenReturn(true);

        ProductTagResponseDashboard created = service.createTag(request("New arrivals", "وصل حديثاً"));

        assertThat(created.getSlug()).isEqualTo("new-arrivals-2");
    }

    @Test
    @DisplayName("renaming leaves the slug alone so filters keep working")
    void renamingKeepsTheSlug() {
        ProductTag existing = tag(7L, "new-arrivals", "New arrivals", "وصل حديثاً");
        when(tagRepository.findById(7L)).thenReturn(Optional.of(existing));

        ProductTagResponseDashboard renamed = service.renameTag(7L, request("Just landed", "وصل للتو"));

        assertThat(renamed.getSlug()).isEqualTo("new-arrivals");
        assertThat(existing.getTranslations())
                .extracting(ProductTagTranslation::getName)
                .containsExactlyInAnyOrder("Just landed", "وصل للتو");
    }

    @Test
    @DisplayName("a tag products still carry is refused with the count")
    void refusesDeletingATagInUse() {
        ProductTag inUse = tag(3L, "gift-boxes", "Gift boxes", "علب الهدايا");
        when(tagRepository.findById(3L)).thenReturn(Optional.of(inUse));
        doThrow(new EntityInUseException("error.delete.in.use.products", 4))
                .when(trashRules).refuseIfReferenced(Reference.PRODUCT_TAG_LINK, 3L);

        assertThatThrownBy(() -> service.deleteTag(3L))
                .isInstanceOf(EntityInUseException.class)
                .satisfies(e -> assertThat(((EntityInUseException) e).getCount()).isEqualTo(4));

        // The refusal must leave the row live: trashing it would hide the label its products
        // show, and nothing would bring the products back without a restore.
        assertThat(inUse.getDeletedAt()).isNull();
        verify(tagRepository, never()).save(any());
    }

    @Test
    @DisplayName("an unused tag is soft-deleted, not erased")
    void softDeletesAnUnusedTag() {
        ProductTag unused = tag(5L, "summer", "Summer", "الصيف");
        when(tagRepository.findById(5L)).thenReturn(Optional.of(unused));

        service.deleteTag(5L);

        assertThat(unused.getDeletedAt()).isNotNull();
        verify(tagRepository).save(unused);
    }

    @Test
    @DisplayName("an unknown tag slug is rejected instead of silently dropped")
    void refusesUnknownSlugs() {
        when(tagRepository.findAllBySlugIn(List.of("ghost"))).thenReturn(List.of());

        assertThatThrownBy(() -> service.replaceTags(
                com.nocteon.nocteon_api.product.entity.Product.builder().id(1L).slug("yirgacheffe").build(),
                List.of("ghost")))
                .isInstanceOf(ProductTagNotFoundException.class);
    }

    private static ProductTagRequest request(String en, String ar) {
        ProductTagRequest request = new ProductTagRequest();
        ProductTagTranslationRequest english = new ProductTagTranslationRequest();
        english.setLanguage("en");
        english.setName(en);
        ProductTagTranslationRequest arabic = new ProductTagTranslationRequest();
        arabic.setLanguage("ar");
        arabic.setName(ar);
        request.setTranslations(List.of(english, arabic));
        return request;
    }

    private static ProductTag tag(Long id, String slug, String en, String ar) {
        ProductTag tag = ProductTag.builder().id(id).slug(slug).build();
        tag.getTranslations().add(ProductTagTranslation.builder()
                .tag(tag).language("en").name(en).build());
        tag.getTranslations().add(ProductTagTranslation.builder()
                .tag(tag).language("ar").name(ar).build());
        return tag;
    }
}
