package com.nocteon.nocteon_api.order.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.order.service.UnpaidOrderExpiryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin trigger for the unpaid-order sweep — all behaviour lives in
 * {@link UnpaidOrderExpiryService}, which is unit-tested directly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UnpaidOrderExpiryScheduler {

    private final UnpaidOrderExpiryService expiryService;

    @Value("${application.orders.expiry-enabled:true}")
    private boolean expiryEnabled;

    @Scheduled(cron = "0 */15 * * * *")
    public void releaseExpiredCheckouts() {
        if (!expiryEnabled) {
            log.debug("Unpaid-order expiry is disabled, skipping the sweep");
            return;
        }
        int released = expiryService.cancelExpiredUnpaidOrders();
        log.info("Unpaid-order sweep finished, released={}", released);
    }
}
