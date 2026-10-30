package com.nocteon.nocteon_api.audit;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.audit.config.AuditRetentionProperties;
import com.nocteon.nocteon_api.audit.scheduler.AuditLogRetentionScheduler;
import com.nocteon.nocteon_api.audit.service.AuditLogRetentionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The scheduler is a gate and nothing else, so this pins the gate.
 *
 * <p>Deleting is the expensive failure mode here: if the gate leaked, a deployment
 * that never asked for retention would start truncating {@code audit_logs} at
 * 03:30 and nobody would notice until the history was gone. The proof that a
 * disabled deployment touches nothing is that the service is never called at
 * all — hence {@code verifyNoInteractions}, not "was not called with".
 *
 * <p>The complementary proof, against the real database, is in
 * {@code AuditLogRetentionIntegrationTest} — a scheduler built from the default
 * properties leaves the rows in the table.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogRetentionSchedulerTest {

    @Mock
    private AuditRetentionProperties properties;

    @Mock
    private AuditLogRetentionService retentionService;

    @InjectMocks
    private AuditLogRetentionScheduler scheduler;

    @Test
    void runIsANoOpWhenRetentionIsDisabled() {
        when(properties.isEnabled()).thenReturn(false);

        scheduler.run();

        verifyNoInteractions(retentionService);
    }

    @Test
    void runCallsTheServiceWhenEnabled() {
        when(properties.isEnabled()).thenReturn(true);

        scheduler.run();

        verify(retentionService).runRetention();
    }

    @Test
    void theScheduledMethodHoldsNoTransaction() throws NoSuchMethodException {
        Method run = AuditLogRetentionScheduler.class.getMethod("run");

        assertThat(run.getAnnotation(Transactional.class))
                .as("run() must not be @Transactional — the proxy would open a transaction "
                        + "before the gate ran, so a disabled deployment would take a connection "
                        + "every night")
                .isNull();
    }

    /**
     * 03:30, nightly: not 03:00, which already belongs to the AI reindex and the
     * notification sweep, and not a narrower schedule — a sweep that runs once a
     * month lets a backlog grow for 29 days and then deletes it all at once.
     */
    @Test
    void runIsRegisteredOnANightlyCron() throws NoSuchMethodException {
        Scheduled scheduled = AuditLogRetentionScheduler.class.getMethod("run").getAnnotation(Scheduled.class);

        assertThat(scheduled).as("@Scheduled is what makes the pass happen at all").isNotNull();
        assertThat(scheduled.cron())
                .as("seconds minutes hours day-of-month month day-of-week")
                .isEqualTo("0 30 3 * * *");
    }
}
