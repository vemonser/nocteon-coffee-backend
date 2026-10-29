package com.nocteon.nocteon_api.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.audit.config.AuditRetentionProperties;
import com.nocteon.nocteon_api.audit.entity.AuditLog;
import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.audit.repository.AuditLogRepository;
import com.nocteon.nocteon_api.audit.scheduler.AuditLogRetentionScheduler;
import com.nocteon.nocteon_api.audit.service.AuditLogRetentionService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * Retention against the real database.
 *
 * <p>This suite runs against the shared development database, so isolation is
 * not optional. Two rules keep it honest:
 *
 * <ol>
 * <li>Every row this test writes carries an actor e-mail no seed and no other
 * test uses, and every query it asserts on is narrowed by that e-mail. No test
 * here ever asserts a global row count.</li>
 * <li>The window is 100 years, not 180 days. {@code audit_logs} on a developer
 * machine already holds rows from other suites and from manual poking; with a
 * 180-day horizon a leftover row from a crashed run would be deleted by this
 * test and would also break its "exactly five deleted" assertion. With
 * 36 500 days the horizon lands in 1926 — before anything any suite has ever
 * written — and the test rows are placed in 1900, so the sweep can only ever
 * see rows this test owns.</li>
 * </ol>
 *
 * <p>Rows are created through the repository (so the entity — and its auditing —
 * is exercised) and their {@code created_at} is then forced backwards with SQL,
 * because {@code created_at} is {@code updatable = false} on {@code BaseEntity}
 * and the point of the test is a timestamp, not a clock.
 *
 * <p>The three symptoms of a broken batch loop are covered separately: the
 * single statement is capped at {@code batch-size}, the pass drains more than one
 * batch, and the pass right after it does nothing.
 */
@SpringBootTest(
        classes = NocteonApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AuditLogRetentionIntegrationTest {

    /** An actor no seed, no other test and no fixture uses. */
    private static final String ACTOR_EMAIL = "audit-retention-sweep@example.test";
    private static final Long ACTOR_ID = 9_999_101L;

    /**
     * 100 years, so the horizon (1926) is older than every row the shared
     * database holds today: only the rows this test backdates to 1900 can match.
     */
    private static final int DAYS = 36_500;

    /** Small on purpose: five old rows then take three statements, not one. */
    private static final int BATCH_SIZE = 2;

    /** Five rows older than the horizon: the backlog to drain. */
    private static final int OLD_ROWS = 5;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuditLogRetentionService retentionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Ids of the rows this test inserted, newest last. */
    private final List<Long> ownIds = new ArrayList<>();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
        registry.add("app.audit.retention.enabled", () -> "true");
        registry.add("app.audit.retention.days", () -> Integer.toString(DAYS));
        registry.add("app.audit.retention.batch-size", () -> Integer.toString(BATCH_SIZE));
    }

    @BeforeEach
    void setUp() {
        deleteOwnRows();
        ownIds.clear();

        for (int i = 0; i < OLD_ROWS; i++) {
            Long id = insertRow(1_000L + i);
            backdate(id, Instant.parse("1900-01-0" + (i + 1) + "T09:00:00Z"));
        }
        // Inside the window: two "just happened" rows and one one day inside it.
        insertRow(2_000L);
        insertRow(2_001L);
        backdate(insertRow(2_002L), Instant.now().minus(DAYS - 1L, ChronoUnit.DAYS));
    }

    @AfterEach
    void tearDown() {
        deleteOwnRows();
        ownIds.clear();
    }

    @Test
    void rowsOlderThanTheHorizonAreDeletedAndRowsInsideItAreKept() {
        assertThat(countOlderThanHorizon()).as("the five backdated rows").isEqualTo(OLD_ROWS);
        assertThat(countInsideHorizon()).as("two fresh rows + one one day inside").isEqualTo(3);

        int deleted = retentionService.runRetention();

        assertThat(deleted).isEqualTo(OLD_ROWS);
        assertThat(countOwnRows()).isEqualTo(3);
        assertThat(countOlderThanHorizon()).isZero();
        assertThat(countInsideHorizon())
                .as("the row one day inside the horizon must survive: the horizon is a floor")
                .isEqualTo(3);
    }

    @Test
    void aSingleStatementIsCappedAtTheBatchSize() {
        int deleted = retentionService.deleteBatch(
                Instant.now().minus(DAYS, ChronoUnit.DAYS), BATCH_SIZE);

        assertThat(deleted)
                .as("one statement must never delete more than batch-size rows")
                .isEqualTo(BATCH_SIZE);
        assertThat(countOlderThanHorizon()).isEqualTo(OLD_ROWS - BATCH_SIZE);
    }

    @Test
    void thePassDrainsMoreThanOneBatch() {
        // Five old rows at two per statement: the loop must go round at least
        // three times, and still finish in one call.
        int deleted = retentionService.runRetention();

        assertThat(deleted).isEqualTo(OLD_ROWS);
        assertThat(countOlderThanHorizon()).isZero();
    }

    @Test
    void aSecondRunIsANoOp() {
        assertThat(retentionService.runRetention()).isEqualTo(OLD_ROWS);

        int second = retentionService.runRetention();

        assertThat(second).as("nothing is left past the horizon, so nothing to delete").isZero();
        assertThat(countOwnRows()).isEqualTo(3);
    }

    /**
     * The gate, end to end with the real service: a scheduler holding the
     * default properties (enabled = false) must leave every row alone.
     */
    @Test
    void aDisabledSchedulerLeavesTheRowsInPlace() {
        AuditRetentionProperties disabled = new AuditRetentionProperties();
        assertThat(disabled.isEnabled()).as("the shipped default is off").isFalse();
        disabled.setDays(DAYS);
        disabled.setBatchSize(BATCH_SIZE);

        new AuditLogRetentionScheduler(disabled, retentionService).run();

        assertThat(countOwnRows()).isEqualTo(OLD_ROWS + 3);
        assertThat(countOlderThanHorizon()).isEqualTo(OLD_ROWS);
    }

    private Long insertRow(long entityId) {
        AuditLog saved = auditLogRepository.save(AuditLog.builder()
                .userId(ACTOR_ID)
                .userEmail(ACTOR_EMAIL)
                .action(AuditAction.USER_LOGIN)
                .entityType("audit-retention-test")
                .entityId(entityId)
                .success(true)
                .build());
        ownIds.add(saved.getId());
        return saved.getId();
    }

    private void backdate(Long id, Instant createdAt) {
        jdbcTemplate.update(
                "UPDATE public.audit_logs SET created_at = ? WHERE id = ?",
                Timestamp.from(createdAt), id);
    }

    private void deleteOwnRows() {
        jdbcTemplate.update("DELETE FROM public.audit_logs WHERE user_email = ?", ACTOR_EMAIL);
    }

    private long countOwnRows() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.audit_logs WHERE user_email = ?",
                Long.class, ACTOR_EMAIL);
    }

    /** Rows this test owns that sit before the horizon, computed the same way. */
    private long countOlderThanHorizon() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.audit_logs WHERE user_email = ? AND created_at < ?",
                Long.class, ACTOR_EMAIL, Timestamp.from(Instant.now().minus(DAYS, ChronoUnit.DAYS)));
    }

    private long countInsideHorizon() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.audit_logs WHERE user_email = ? AND created_at >= ?",
                Long.class, ACTOR_EMAIL, Timestamp.from(Instant.now().minus(DAYS, ChronoUnit.DAYS)));
    }
}
