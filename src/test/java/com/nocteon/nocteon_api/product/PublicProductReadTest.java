package com.nocteon.nocteon_api.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.product.dto.request.ProductFilterRequest;
import com.nocteon.nocteon_api.product.dto.response.ProductResponse;
import com.nocteon.nocteon_api.product.service.ProductService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * The storefront reads the catalogue with no dashboard shell around it: the
 * list and the detail used to answer 500 with open-in-view off (lazy
 * translations, variants and option values).
 */
@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PublicProductReadTest {

    @Autowired
    private ProductService productService;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void thePublicListBuildsOutsideAnOpenSession() {
        assertThatCode(() -> productService.getAll(new ProductFilterRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    void thePublicDetailBuildsAndCarriesTheLocalizedStory() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("ar"));

        ProductResponse response = productService.getBySlug("brazil-mogiana");

        assertThat(response).isNotNull();
        assertThat(response.getCoffeeDetails()).isNotNull();
        // The story is per language now; the seed copied the old column into
        // every translation, so an Arabic read still has one.
        assertThat(response.getCoffeeDetails().getStory()).isNotBlank();
    }
}
