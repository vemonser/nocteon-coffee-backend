package com.nocteon.nocteon_api.mail.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EmailRetryScheduler {

    private final TransactionalEmailService  emailService;

    @Scheduled(fixedDelay = 60_000) 
    public void run() {
        emailService.retryPendingEmails();
    }
}