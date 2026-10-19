package com.nocteon.nocteon_api.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.support.SqlCapture;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * Regression guard for the "in-memory pagination" defect (queue item #2).
 *
 * <p>The response body cannot distinguish SQL-level pagination from in-memory
 * pagination: Hibernate slices the page correctly either way and reports the same
 * {@code totalElements}. The only observable is the generated SQL, which is why this
 * test inspects it through {@link SqlCapture} (a {@code StatementInspector}).
 *
 * <p>If a future change (ORM downgrade, refactor of {@code findAllDashboard}, removal of
 * the {@code fail_on_pagination_over_collection_fetch} property) makes Hibernate fall
 * back to in-memory pagination, this test fails instead of silently reading the whole
 * table.
 */
@SpringBootTest(
        classes = NocteonApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.nocteon.nocteon_api.support.SqlCapture")
class ProductPaginationGuardTest {

    @Autowired
    private ProductRepository productRepository;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void findAllDashboardPaginatesInSqlNotInMemory() {
        SqlCapture.start();
        try {
            productRepository.findAllDashboard("", null, null, null, null, PageRequest.of(0, 5));
        } finally {
            // keep capture running until we have read it
        }
        List<String> sqls = SqlCapture.stop();

        String rowSql = sqls.stream()
                .filter(s -> s.contains("from products p1_0") && !s.toLowerCase().startsWith("select count"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no products row query was issued"));

        assertThat(rowSql)
                .as("the row limit must be applied by the database (derived-table strategy)")
                .containsIgnoringCase("offset")
                .containsIgnoringCase("fetch first");

        // the limit must sit INSIDE the distinct sub-query, before the pairings join,
        // otherwise the collection fetch multiplies the rows before pagination
        int limitPos = rowSql.toLowerCase().indexOf("fetch first");
        int joinPos = rowSql.toLowerCase().indexOf("left join product_pairings");
        assertThat(limitPos).isGreaterThan(0);
        assertThat(joinPos).isGreaterThan(limitPos);

        assertThat(rowSql)
                .as("the collection fetch join that motivated the check is still present")
                .containsIgnoringCase("left join product_pairings");
    }

    @Test
    void findAllDashboardSecondPageStillPaginatesInSql() {
        SqlCapture.start();
        productRepository.findAllDashboard("", null, null, null, null, PageRequest.of(1, 5));
        List<String> sqls = SqlCapture.stop();

        String rowSql = sqls.stream()
                .filter(s -> s.contains("from products p1_0") && !s.toLowerCase().startsWith("select count"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no products row query was issued"));

        assertThat(rowSql)
                .containsIgnoringCase("offset")
                .containsIgnoringCase("fetch first");
    }
}
