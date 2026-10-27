package com.nocteon.nocteon_api.dashboard;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.brewingMethod.service.BrewingMethodService;
import com.nocteon.nocteon_api.category.service.CategoryService;
import com.nocteon.nocteon_api.coffeeVariety.service.CoffeeVarietyService;
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.farm.service.FarmService;
import com.nocteon.nocteon_api.origin.service.OriginService;
import com.nocteon.nocteon_api.pairing.service.PairingService;
import com.nocteon.nocteon_api.processingMethod.service.ProcessingMethodService;
import com.nocteon.nocteon_api.product.dto.request.ProductFilterRequest;
import com.nocteon.nocteon_api.product.service.ProductOptionService;
import com.nocteon.nocteon_api.product.service.ProductService;
import com.nocteon.nocteon_api.shippingZone.service.ShippingZoneService;
import com.nocteon.nocteon_api.roastLevel.service.RoastLevelService;
import com.nocteon.nocteon_api.support.TestJwtKeys;
import com.nocteon.nocteon_api.tastingNote.service.TastingNoteService;

/**
 * Every dashboard catalog list must map its rows outside an open session.
 *
 * <p>{@code spring.jpa.open-in-view=false} is deliberate. Each of these
 * responses reads a lazily loaded {@code translations} collection, so the
 * read method has to stay transactional (or fetch the collection) or the
 * admin gets a 500 on every lookup screen. Nine endpoints were broken this
 * way at once: categories, origins, roast levels, brewing methods, coffee
 * varieties, processing methods, tasting notes, pairings and products.
 */
@SpringBootTest(
        classes = NocteonApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DashboardLookupListsIntegrationTest {

    @Autowired
    private CategoryService categoryService;
    @Autowired
    private OriginService originService;
    @Autowired
    private FarmService farmService;
    @Autowired
    private RoastLevelService roastLevelService;
    @Autowired
    private BrewingMethodService brewingMethodService;
    @Autowired
    private CoffeeVarietyService coffeeVarietyService;
    @Autowired
    private ProcessingMethodService processingMethodService;
    @Autowired
    private TastingNoteService tastingNoteService;
    @Autowired
    private PairingService pairingService;
    @Autowired
    private ProductService productService;
    @Autowired
    private ProductOptionService productOptionService;
    @Autowired
    private ShippingZoneService shippingZoneService;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void categoriesListMapsOutsideTheSession() {
        assertThatCode(() -> categoryService.getAllDashboard(new LookupFilterRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    void originsListMapsOutsideTheSession() {
        assertThatCode(() -> originService.getAllDashboard(new LookupFilterRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    void farmsListMapsOutsideTheSession() {
        assertThatCode(() -> farmService.getAllDashboard(new com.nocteon.nocteon_api.farm.dto.request.FarmFilterRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    void roastLevelsListMapsOutsideTheSession() {
        assertThatCode(() -> roastLevelService.getAllDashboard(new LookupFilterRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    void brewingMethodsListMapsOutsideTheSession() {
        assertThatCode(() -> brewingMethodService.getAllDashboard(new LookupFilterRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    void coffeeVarietiesListMapsOutsideTheSession() {
        assertThatCode(() -> coffeeVarietyService.getAllDashboard(new LookupFilterRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    void processingMethodsListMapsOutsideTheSession() {
        assertThatCode(() -> processingMethodService.getAllDashboard(new LookupFilterRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    void tastingNotesListMapsOutsideTheSession() {
        assertThatCode(() -> tastingNoteService.getAllDashboard(new LookupFilterRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    void pairingsListMapsOutsideTheSession() {
        assertThatCode(() -> pairingService.getAllDashboard(new LookupFilterRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    void productOptionsListMapsOutsideTheSession() {
        assertThatCode(() -> productOptionService.getAllDashboard(new LookupFilterRequest(), null))
                .doesNotThrowAnyException();
    }

    @Test
    void shippingZonesListMapsOutsideTheSession() {
        assertThatCode(() -> shippingZoneService.getAll(new com.nocteon.nocteon_api.common.dto.BaseFilterRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    void productsListMapsOutsideTheSession() {
        assertThatCode(() -> productService.getAllDashboard(new ProductFilterRequest()))
                .doesNotThrowAnyException();
    }
}
