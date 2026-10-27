package com.nocteon.nocteon_api.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.dashboard.dto.DashboardOverviewResponse;
import com.nocteon.nocteon_api.dashboard.dto.RevenueByCategoryDto;
import com.nocteon.nocteon_api.dashboard.service.DashboardService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * The overview must build outside an open Hibernate session.
 *
 * <p>{@code spring.jpa.open-in-view=false} is deliberate, so every lazily
 * loaded collection the response touches has to be fetched by its query. The
 * revenue-by-category path used the plain {@code findBySlugIn} and blew up
 * with "Cannot lazily initialize collection of role Category.translations"
 * on every dashboard load — a 500 on the store's landing page.
 */
@SpringBootTest(
        classes = NocteonApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DashboardOverviewIntegrationTest {

    @Autowired
    private DashboardService dashboardService;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void theOverviewBuildsWithoutAnOpenSession() {
        assertThatCode(() -> dashboardService.getOverview(30)).doesNotThrowAnyException();
    }

    @Test
    void theComparisonOverviewBuildsWithoutAnOpenSession() {
        assertThatCode(() -> dashboardService.getOverviewWithPrevious(30))
                .doesNotThrowAnyException();
    }

    @Test
    void categoryRevenueCarriesTheTranslatedNameNotJustTheSlug() {
        DashboardOverviewResponse overview = dashboardService.getOverview(30);

        assertThat(overview.getRevenueByCategory())
                .allSatisfy((RevenueByCategoryDto row) -> assertThat(row.getCategoryName()).isNotBlank());
    }
}
