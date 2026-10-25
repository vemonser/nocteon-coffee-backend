package com.nocteon.nocteon_api.cloudinary.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Read-only diagnostic that reports Cloudinary assets under {@code nocteon/} which
 * are not referenced by any database row. It never deletes or modifies anything.
 *
 * <p>Invocation: enable {@code cloudinary.reconcile.enabled=true} (default false)
 * and run the application; the diagnostic runs once at startup and logs a report.
 * It can also be called programmatically via {@link #runDiagnostic()}.
 */
@Service
@Slf4j
public class CloudinaryReconciliationService {

    private static final Logger LOG = LoggerFactory.getLogger(CloudinaryReconciliationService.class);
    private static final int PAGE_SIZE = 500;
    private static final int MAX_PAGES = 100;
    private static final int MAX_RETRIES = 3;

    private final Cloudinary cloudinary;
    private final JdbcTemplate jdbcTemplate;
    private final long gracePeriodHours;

    public CloudinaryReconciliationService(Cloudinary cloudinary, JdbcTemplate jdbcTemplate,
                                          @Value("${cloudinary.reconcile.grace-period-hours:24}") long gracePeriodHours) {
        this.cloudinary = cloudinary;
        this.jdbcTemplate = jdbcTemplate;
        this.gracePeriodHours = gracePeriodHours;
    }

    public ReconciliationReport runDiagnostic() {
        // 1. Collect referenced public IDs from all seven URL sources.
        Set<String> referenced;
        try {
            referenced = collectReferencedPublicIds();
        } catch (Exception e) {
            LOG.error("Reconciliation aborted: failed to collect DB references", e);
            return ReconciliationReport.incomplete("DB reference collection failed: " + e.getMessage());
        }

        // 2. List all Cloudinary assets under nocteon/ (paginated).
        List<CloudinaryAsset> assets = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        try {
            do {
                Map<String, Object> result = listPage(cursor);
                List<Map<String, Object>> resources = (List<Map<String, Object>>) result.get("resources");
                if (resources != null) {
                    for (Map<String, Object> r : resources) {
                        assets.add(new CloudinaryAsset(
                                String.valueOf(r.get("public_id")),
                                String.valueOf(r.get("secure_url")),
                                String.valueOf(r.get("created_at"))));
                    }
                }
                Object next = result.get("next_cursor");
                String nextCursor = (next == null || "null".equals(String.valueOf(next))) ? null : String.valueOf(next);
                // Treat empty/whitespace-only cursors as terminal to avoid repeating a page.
                if (nextCursor != null && nextCursor.trim().isEmpty()) {
                    nextCursor = null;
                }
                cursor = nextCursor;
                pages++;
            } while (cursor != null && pages < MAX_PAGES);

            // If the page limit was reached while a cursor remains, the inventory is
            // truncated and must NOT be reported as complete.
            if (cursor != null) {
                LOG.warn("Cloudinary listing truncated after {} pages; more assets may exist", MAX_PAGES);
                return ReconciliationReport.incomplete("Cloudinary listing truncated at " + MAX_PAGES
                        + " pages; more assets may exist. Increase MAX_PAGES or paginate manually.");
            }
        } catch (Exception e) {
            LOG.error("Reconciliation aborted: Cloudinary listing failed", e);
            return ReconciliationReport.incomplete("Cloudinary listing failed: " + e.getMessage());
        }

        // 3. Determine candidates: not referenced AND older than grace period.
        Instant cutoff = Instant.now().minus(gracePeriodHours, ChronoUnit.HOURS);
        List<String> candidateIds = new ArrayList<>();
        List<String> candidateUrls = new ArrayList<>();
        for (CloudinaryAsset a : assets) {
            if (referenced.contains(a.publicId)) {
                continue;
            }
            Instant created = parseInstant(a.createdAt);
            if (created == null || created.isAfter(cutoff)) {
                continue; // grace period: skip recent assets (may be in-flight)
            }
            candidateIds.add(a.publicId);
            candidateUrls.add(a.url);
        }

        ReconciliationReport report = new ReconciliationReport(
                true, null, referenced.size(), assets.size(), candidateIds.size(),
                candidateIds, candidateUrls, gracePeriodHours);

        LOG.info("Cloudinary reconciliation (dry-run): {} assets scanned, {} referenced, {} candidates older than {}h",
                assets.size(), referenced.size(), candidateIds.size(), gracePeriodHours);
        if (!candidateIds.isEmpty()) {
            LOG.warn("Potential orphaned assets (NOT deleted): {}", candidateIds);
        }
        return report;
    }

    private Set<String> collectReferencedPublicIds() {
        Set<String> ids = new HashSet<>();
        collectFromColumn("SELECT image_url FROM categories WHERE image_url IS NOT NULL", ids);
        collectFromColumn("SELECT image_url FROM origins WHERE image_url IS NOT NULL", ids);
        collectFromColumn("SELECT image_url FROM farms WHERE image_url IS NOT NULL", ids);
        collectFromColumn("SELECT image_url FROM pairings WHERE image_url IS NOT NULL", ids);
        collectFromColumn("SELECT cover_image_url FROM journal_posts WHERE cover_image_url IS NOT NULL", ids);
        collectFromColumn("SELECT url FROM product_media WHERE url IS NOT NULL", ids);
        collectFromColumn("SELECT avatar_url FROM user_profiles WHERE avatar_url IS NOT NULL", ids);
        return ids;
    }

    private void collectFromColumn(String sql, Set<String> ids) {
        List<String> urls = jdbcTemplate.queryForList(sql, String.class);
        for (String url : urls) {
            String pid = CloudinaryService.extractPublicId(url);
            if (pid != null) {
                ids.add(pid);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> listPage(String cursor) throws Exception {
        int attempt = 0;
        while (true) {
            try {
                Map<String, Object> params = new java.util.HashMap<>();
                params.put("type", "upload");
                params.put("prefix", "nocteon/");
                params.put("max_results", PAGE_SIZE);
                if (cursor != null) {
                    params.put("next_cursor", cursor);
                }
                return cloudinary.api().resources(params);
            } catch (Exception e) {
                attempt++;
                if (attempt >= MAX_RETRIES) {
                    throw e;
                }
                Thread.sleep(1000L * attempt);
            }
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            try {
                return java.time.OffsetDateTime.parse(value).toInstant();
            } catch (Exception e2) {
                return null;
            }
        }
    }

    /** Immutable diagnostic result. {@code complete=false} means the report is unreliable. */
    public record ReconciliationReport(
            boolean complete,
            String error,
            int referencedPublicIdCount,
            int totalAssetsScanned,
            int candidateCount,
            List<String> candidatePublicIds,
            List<String> candidateUrls,
            long gracePeriodHours) {

        public static ReconciliationReport incomplete(String error) {
            return new ReconciliationReport(false, error, 0, 0, 0, List.of(), List.of(), 0);
        }
    }

    private record CloudinaryAsset(String publicId, String url, String createdAt) {
    }
}
