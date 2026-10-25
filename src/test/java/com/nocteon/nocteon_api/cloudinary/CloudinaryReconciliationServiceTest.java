package com.nocteon.nocteon_api.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.api.ApiResponse;
import com.cloudinary.Api;
import com.nocteon.nocteon_api.cloudinary.service.CloudinaryReconciliationService;
import com.nocteon.nocteon_api.cloudinary.service.CloudinaryReconciliationService.ReconciliationReport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

class CloudinaryReconciliationServiceTest {

    private JdbcTemplate mockJdbc() {
        return mock(JdbcTemplate.class);
    }

    private Cloudinary mockCloudinary() {
        return mock(Cloudinary.class);
    }

    @Test
    void nonCloudinaryUrlsAreExcludedFromReferencedSet() throws Exception {
        JdbcTemplate jdbc = mockJdbc();
        Cloudinary cloudinary = mockCloudinary();
        // all 7 sources return a mix of cloudinary and non-cloudinary URLs
        org.mockito.Mockito.when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(List.of("https://example.com/foo.png", "https://res.cloudinary.com/x/image/upload/nocteon/categories/abc.jpg"));

        Api api = mock(Api.class);
        when(cloudinary.api()).thenReturn(api);
        ApiResponse emptyPage = mock(ApiResponse.class);
        when(emptyPage.get("resources")).thenReturn(List.of());
        when(emptyPage.get("next_cursor")).thenReturn(null);
        when(api.resources(anyMap())).thenReturn(emptyPage);

        CloudinaryReconciliationService svc = new CloudinaryReconciliationService(cloudinary, jdbc, 24);
        // set grace period via reflection not needed; default 24h. Use a helper? Field is @Value; default 0?
        // We'll just run; referenced count should be 1 (only the cloudinary url).
        ReconciliationReport report = svc.runDiagnostic();
        assertThat(report.referencedPublicIdCount()).isEqualTo(1);
    }

    @Test
    void paginationAcrossMultipleCursorsCollectsAllAssets() throws Exception {
        JdbcTemplate jdbc = mockJdbc();
        Cloudinary cloudinary = mockCloudinary();
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(List.of());

        Api api = mock(Api.class);
        when(cloudinary.api()).thenReturn(api);

        ApiResponse page1 = mock(ApiResponse.class);
        when(page1.get("resources")).thenReturn(List.of(Map.of("public_id", "nocteon/a.png", "secure_url", "https://x/nocteon/a.png", "created_at", "2020-01-01T00:00:00Z")));
        when(page1.get("next_cursor")).thenReturn("cursor2");
        ApiResponse page2 = mock(ApiResponse.class);
        when(page2.get("resources")).thenReturn(List.of(Map.of("public_id", "nocteon/b.png", "secure_url", "https://x/nocteon/b.png", "created_at", "2020-01-01T00:00:00Z")));
        when(page2.get("next_cursor")).thenReturn(null);
        when(api.resources(anyMap())).thenReturn(page1, page2);

        CloudinaryReconciliationService svc = new CloudinaryReconciliationService(cloudinary, jdbc, 24);
        ReconciliationReport report = svc.runDiagnostic();
        assertThat(report.totalAssetsScanned()).isEqualTo(2);
        assertThat(report.complete()).isTrue();
    }

    @Test
    void gracePeriodFiltersRecentAssets() throws Exception {
        JdbcTemplate jdbc = mockJdbc();
        Cloudinary cloudinary = mockCloudinary();
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(List.of());

        Api api = mock(Api.class);
        when(cloudinary.api()).thenReturn(api);
        String recent = Instant.now().toString();
        String old = Instant.now().minusSeconds(48 * 3600).toString();
        ApiResponse page = mock(ApiResponse.class);
        when(page.get("resources")).thenReturn(List.of(
                Map.of("public_id", "nocteon/recent.png", "secure_url", "https://x/nocteon/recent.png", "created_at", recent),
                Map.of("public_id", "nocteon/old.png", "secure_url", "https://x/nocteon/old.png", "created_at", old)));
        when(page.get("next_cursor")).thenReturn(null);
        when(api.resources(anyMap())).thenReturn(page);

        CloudinaryReconciliationService svc = new CloudinaryReconciliationService(cloudinary, jdbc, 24);
        ReconciliationReport report = svc.runDiagnostic();
        // only the old one is a candidate (recent filtered by grace period)
        assertThat(report.candidateCount()).isEqualTo(1);
        assertThat(report.candidatePublicIds()).containsExactly("nocteon/old.png");
    }

    @Test
    void referencedAssetsAreExcluded() throws Exception {
        JdbcTemplate jdbc = mockJdbc();
        Cloudinary cloudinary = mockCloudinary();
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(List.of("https://res.cloudinary.com/x/image/upload/nocteon/categories/ref.jpg"));

        Api api = mock(Api.class);
        when(cloudinary.api()).thenReturn(api);
        ApiResponse page = mock(ApiResponse.class);
        when(page.get("resources")).thenReturn(List.of(
                Map.of("public_id", "nocteon/categories/ref", "secure_url", "https://x/nocteon/categories/ref.jpg", "created_at", "2020-01-01T00:00:00Z"),
                Map.of("public_id", "nocteon/categories/orphan", "secure_url", "https://x/nocteon/categories/orphan.jpg", "created_at", "2020-01-01T00:00:00Z")));
        when(page.get("next_cursor")).thenReturn(null);
        when(api.resources(anyMap())).thenReturn(page);

        CloudinaryReconciliationService svc = new CloudinaryReconciliationService(cloudinary, jdbc, 24);
        ReconciliationReport report = svc.runDiagnostic();
        assertThat(report.candidateCount()).isEqualTo(1);
    }

    @Test
    void dbQueryFailureMarksReportIncomplete() throws Exception {
        JdbcTemplate jdbc = mockJdbc();
        Cloudinary cloudinary = mockCloudinary();
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenThrow(new RuntimeException("DB down"));

        CloudinaryReconciliationService svc = new CloudinaryReconciliationService(cloudinary, jdbc, 24);
        ReconciliationReport report = svc.runDiagnostic();
        assertThat(report.complete()).isFalse();
        assertThat(report.error()).contains("DB reference collection failed");
        assertThat(report.candidateCount()).isEqualTo(0);
    }

    @Test
    void cloudinaryListingFailureMarksReportIncomplete() throws Exception {
        JdbcTemplate jdbc = mockJdbc();
        Cloudinary cloudinary = mockCloudinary();
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(List.of());

        Api api = mock(Api.class);
        when(cloudinary.api()).thenReturn(api);
        when(api.resources(anyMap())).thenThrow(new RuntimeException("Cloudinary down"));

        CloudinaryReconciliationService svc = new CloudinaryReconciliationService(cloudinary, jdbc, 24);
        ReconciliationReport report = svc.runDiagnostic();
        assertThat(report.complete()).isFalse();
        assertThat(report.error()).contains("Cloudinary listing failed");
    }

    @Test
    void maxPageTruncationMarksReportIncomplete() throws Exception {
        JdbcTemplate jdbc = mockJdbc();
        Cloudinary cloudinary = mockCloudinary();
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(List.of());

        Api api = mock(Api.class);
        when(cloudinary.api()).thenReturn(api);
        // Every page returns a cursor -> loop hits MAX_PAGES and must report incomplete.
        ApiResponse page = mock(ApiResponse.class);
        when(page.get("resources")).thenReturn(List.of(
                Map.of("public_id", "nocteon/x.png", "secure_url", "https://x/nocteon/x.png", "created_at", "2020-01-01T00:00:00Z")));
        when(page.get("next_cursor")).thenReturn("next");
        when(api.resources(anyMap())).thenReturn(page);

        CloudinaryReconciliationService svc = new CloudinaryReconciliationService(cloudinary, jdbc, 24);
        ReconciliationReport report = svc.runDiagnostic();
        assertThat(report.complete()).isFalse();
        assertThat(report.error()).contains("truncated");
        assertThat(report.candidateCount()).isEqualTo(0);
        assertThat(report.candidatePublicIds()).isEmpty();
    }

    @Test
    void whitespaceOnlyCursorTerminatesPagination() throws Exception {
        JdbcTemplate jdbc = mockJdbc();
        Cloudinary cloudinary = mockCloudinary();
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(List.of());

        Api api = mock(Api.class);
        when(cloudinary.api()).thenReturn(api);
        ApiResponse page = mock(ApiResponse.class);
        when(page.get("resources")).thenReturn(List.of(
                Map.of("public_id", "nocteon/a.png", "secure_url", "https://x/nocteon/a.png", "created_at", "2020-01-01T00:00:00Z")));
        when(page.get("next_cursor")).thenReturn("   "); // whitespace-only cursor -> terminal
        when(api.resources(anyMap())).thenReturn(page);

        CloudinaryReconciliationService svc = new CloudinaryReconciliationService(cloudinary, jdbc, 24);
        ReconciliationReport report = svc.runDiagnostic();
        assertThat(report.complete()).isTrue();
        assertThat(report.totalAssetsScanned()).isEqualTo(1);
    }

    @Test
    void emptyCursorTerminatesPagination() throws Exception {
        JdbcTemplate jdbc = mockJdbc();
        Cloudinary cloudinary = mockCloudinary();
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(List.of());

        Api api = mock(Api.class);
        when(cloudinary.api()).thenReturn(api);
        ApiResponse page = mock(ApiResponse.class);
        when(page.get("resources")).thenReturn(List.of(
                Map.of("public_id", "nocteon/a.png", "secure_url", "https://x/nocteon/a.png", "created_at", "2020-01-01T00:00:00Z")));
        when(page.get("next_cursor")).thenReturn(""); // empty cursor -> terminal
        when(api.resources(anyMap())).thenReturn(page);

        CloudinaryReconciliationService svc = new CloudinaryReconciliationService(cloudinary, jdbc, 24);
        ReconciliationReport report = svc.runDiagnostic();
        assertThat(report.complete()).isTrue();
        assertThat(report.totalAssetsScanned()).isEqualTo(1);
    }

    @Test
    void noCloudinaryDeleteMethodIsCalled() throws Exception {
        JdbcTemplate jdbc = mockJdbc();
        Cloudinary cloudinary = mockCloudinary();
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(List.of());

        Api api = mock(Api.class);
        when(cloudinary.api()).thenReturn(api);
        ApiResponse emptyPage = mock(ApiResponse.class);
        when(emptyPage.get("resources")).thenReturn(List.of());
        when(emptyPage.get("next_cursor")).thenReturn(null);
        when(api.resources(anyMap())).thenReturn(emptyPage);

        CloudinaryReconciliationService svc = new CloudinaryReconciliationService(cloudinary, jdbc, 24);
        svc.runDiagnostic();

        // Cloudinary deletion goes through uploader().destroy(). Verify that uploader()
        // was never invoked (diagnostic is read-only; only api().resources() listing is used).
        verify(cloudinary, never()).uploader();
    }
}
