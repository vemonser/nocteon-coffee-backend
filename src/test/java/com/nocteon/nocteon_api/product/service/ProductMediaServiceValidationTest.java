package com.nocteon.nocteon_api.product.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.nocteon.nocteon_api.cloudinary.service.CloudinaryService;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.product.dto.request.ProductMediaRequest;
import com.nocteon.nocteon_api.product.enums.MediaType;
import com.nocteon.nocteon_api.product.repository.ProductMediaRepository;

/**
 * Editing a product without touching its media must save.
 *
 * <p>The multipart body carries a "files" part only when something is being uploaded. On a
 * plain edit the browser sends none, Spring binds {@code null}, and the old null check
 * rejected the whole save — a 500 on the most common action in the catalogue. These tests
 * pin the difference between "no uploads" and "the wrong number of uploads".
 */
class ProductMediaServiceValidationTest {

    private final ProductMediaService service = new ProductMediaService(
            mock(ProductMediaRepository.class),
            mock(LookupServiceHelper.class),
            mock(CloudinaryService.class));

    @Test
    void anEditThatKeepsEveryExistingMediaPassesWithNoFilesPart() {
        List<ProductMediaRequest> requests = List.of(
                existing(18L, true, 0),
                existing(17L, false, 1));

        assertThatCode(() -> service.validateMediaRequest(requests, null))
                .doesNotThrowAnyException();
    }

    @Test
    void aNewUploadStillNeedsItsFile() {
        List<ProductMediaRequest> requests = List.of(
                existing(18L, true, 0),
                newMedia(false, 1));

        assertThatThrownBy(() -> service.validateMediaRequest(requests, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("files count must match");
    }

    @Test
    void exactlyOneItemMustBePrimary() {
        List<ProductMediaRequest> none = List.of(existing(18L, false, 0));
        assertThatThrownBy(() -> service.validateMediaRequest(none, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be primary");

        List<ProductMediaRequest> two = List.of(
                existing(18L, true, 0),
                existing(17L, true, 1));
        assertThatThrownBy(() -> service.validateMediaRequest(two, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only one media item");
    }

    private static ProductMediaRequest existing(Long id, boolean primary, int order) {
        return ProductMediaRequest.builder()
                .id(id)
                .type(MediaType.IMAGE)
                .isPrimary(primary)
                .sortOrder(order)
                .build();
    }

    private static ProductMediaRequest newMedia(boolean primary, int order) {
        return ProductMediaRequest.builder()
                .type(MediaType.IMAGE)
                .isPrimary(primary)
                .sortOrder(order)
                .build();
    }
}
