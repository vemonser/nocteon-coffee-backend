package com.nocteon.nocteon_api.dashboard;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import com.nocteon.nocteon_api.origin.service.OriginService;
import com.nocteon.nocteon_api.pairing.service.PairingService;
import com.nocteon.nocteon_api.processingMethod.service.ProcessingMethodService;
import com.nocteon.nocteon_api.product.dto.request.ProductFilterRequest;
import com.nocteon.nocteon_api.product.service.ProductService;
import com.nocteon.nocteon_api.roastLevel.service.RoastLevelService;
import com.nocteon.nocteon_api.support.TestJwtKeys;
import com.nocteon.nocteon_api.tastingNote.service.TastingNoteService;

import jakarta.persistence.EntityManagerFactory;

/**
 * The query budget for one catalogue page.
 *
 * <p>These lists used to map a lazy {@code translations} collection per row
 * (N+1). Most entities carry {@code @BatchSize(30)}, which collapses a page of
 * 20 into one extra query. This test measures the SQL statements a page costs
 * so a new per-row access cannot quietly reintroduce the N+1.
 *
 * <p>Thresholds are deliberately just above the measured value: the point is
 * "does not grow with the number of rows", not an exact count.
 */
@SpringBootTest(
        classes = NocteonApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class DashboardListQueryBudgetTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private CategoryService categoryService;
    @Autowired
    private OriginService originService;
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

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    private long statements(Runnable call) {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        call.run();
        return stats.getPrepareStatementCount();
    }

    private void assertBudget(String name, long statements, long budget) {
        System.out.printf("QUERY-BUDGET %-18s %3d statements (budget %d)%n", name, statements, budget);
        org.assertj.core.api.Assertions.assertThat(statements)
                .as("%s must not query per row", name)
                .isLessThanOrEqualTo(budget);
    }

    @Test
    void categories() {
        var page = categoryService.getAllDashboard(new LookupFilterRequest());
        System.out.println("ROWS categories " + page.getContent().size());
        long n = statements(() -> categoryService.getAllDashboard(new LookupFilterRequest()));
        assertBudget("categories", n, 4);
    }

    @Test
    void origins() {
        long n = statements(() -> originService.getAllDashboard(new LookupFilterRequest()));
        assertBudget("origins", n, 4);
    }

    @Test
    void roastLevels() {
        long n = statements(() -> roastLevelService.getAllDashboard(new LookupFilterRequest()));
        assertBudget("roastLevels", n, 4);
    }

    @Test
    void brewingMethods() {
        long n = statements(() -> brewingMethodService.getAllDashboard(new LookupFilterRequest()));
        assertBudget("brewingMethods", n, 4);
    }

    @Test
    void coffeeVarieties() {
        long n = statements(() -> coffeeVarietyService.getAllDashboard(new LookupFilterRequest()));
        assertBudget("coffeeVarieties", n, 4);
    }

    @Test
    void processingMethods() {
        long n = statements(() -> processingMethodService.getAllDashboard(new LookupFilterRequest()));
        assertBudget("processingMethods", n, 4);
    }

    @Test
    void tastingNotes() {
        long n = statements(() -> tastingNoteService.getAllDashboard(new LookupFilterRequest()));
        assertBudget("tastingNotes", n, 4);
    }

    @Test
    void pairings() {
        long n = statements(() -> pairingService.getAllDashboard(new LookupFilterRequest()));
        assertBudget("pairings", n, 4);
    }

    @Test
    void products() {
        long n = statements(() -> productService.getAllDashboard(new ProductFilterRequest()));
        assertBudget("products", n, 16);
    }
}
