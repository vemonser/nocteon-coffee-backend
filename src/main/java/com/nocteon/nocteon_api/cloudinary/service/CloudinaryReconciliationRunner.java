package com.nocteon.nocteon_api.cloudinary.service;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Manual, opt-in diagnostic entry point for Cloudinary orphan reconciliation.
 *
 * <p>Enabled only when {@code cloudinary.reconcile.enabled=true} is set (default
 * false). Runs once at startup, invokes {@link CloudinaryReconciliationService#runDiagnostic()},
 * and logs the report. It never deletes or modifies any Cloudinary asset or DB row.
 *
 * <p>Usage: {@code cloudinary.reconcile.enabled=true ./mvnw spring-boot:run}
 */
@Component
@ConditionalOnProperty(name = "cloudinary.reconcile.enabled", havingValue = "true")
public class CloudinaryReconciliationRunner implements CommandLineRunner {

    private final CloudinaryReconciliationService service;

    public CloudinaryReconciliationRunner(CloudinaryReconciliationService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        CloudinaryReconciliationService.ReconciliationReport report = service.runDiagnostic();
        if (!report.complete()) {
            System.err.println("Cloudinary reconciliation INCOMPLETE: " + report.error());
            return;
        }
        System.out.println("Cloudinary reconciliation (dry-run): " + report.totalAssetsScanned()
                + " assets scanned, " + report.referencedPublicIdCount() + " referenced, "
                + report.candidateCount() + " candidates (grace " + report.gracePeriodHours() + "h).");
        for (int i = 0; i < report.candidatePublicIds().size(); i++) {
            System.out.println("  CANDIDATE: " + report.candidatePublicIds().get(i)
                    + "  " + report.candidateUrls().get(i));
        }
        System.out.println("No deletions performed. Review candidates manually before any cleanup.");
    }
}
