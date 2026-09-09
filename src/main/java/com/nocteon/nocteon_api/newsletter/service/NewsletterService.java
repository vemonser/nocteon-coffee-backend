package com.nocteon.nocteon_api.newsletter.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.common.util.HashUtil;
import com.nocteon.nocteon_api.newsletter.entity.NewsletterSubscriber;
import com.nocteon.nocteon_api.newsletter.enums.SubscriptionResult;
import com.nocteon.nocteon_api.newsletter.exception.NewsletterSubscriberNotFoundException;
import com.nocteon.nocteon_api.newsletter.repository.NewsletterSubscriberRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsletterService {

    private final NewsletterSubscriberRepository subscriberRepository;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public SubscriptionResult subscribe(String email) {
        String normalizedEmail = email.trim().toLowerCase();

        Optional<NewsletterSubscriber> existing = subscriberRepository.findByEmailIgnoreCase(normalizedEmail);

        if (existing.isPresent()) {
            NewsletterSubscriber subscriber = existing.get();
            if (subscriber.isSubscribed()) {
                log.info("Newsletter already subscribed: {}", normalizedEmail);
                return SubscriptionResult.ALREADY_SUBSCRIBED;
            }

            subscriber.setSubscribed(true);
            subscriber.setTokenHash(hashToken(generateToken()));
            subscriberRepository.save(subscriber);
            log.info("Newsletter re-subscribed: {}", normalizedEmail);
            return SubscriptionResult.RE_SUBSCRIBED;
        }

        NewsletterSubscriber subscriber = NewsletterSubscriber.builder()
                .email(normalizedEmail)
                .tokenHash(hashToken(generateToken()))
                .subscribed(true)
                .build();

        subscriberRepository.save(subscriber);
        log.info("Newsletter new subscriber: {}", normalizedEmail);
        return SubscriptionResult.SUBSCRIBED;
    }

    @Transactional
    public void unsubscribe(String token) {
        String tokenHash = hashToken(token);

        NewsletterSubscriber subscriber = subscriberRepository.findByTokenHash(tokenHash)
                .orElseThrow(NewsletterSubscriberNotFoundException::new);

        if (!subscriber.isSubscribed()) {
            log.info("Newsletter already unsubscribed: {}", subscriber.getEmail());
            return;
        }

        subscriber.setSubscribed(false);
        subscriberRepository.save(subscriber);

        log.info("Newsletter unsubscribed: {}", subscriber.getEmail());
    }

    String generateToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    String hashToken(String rawToken) {
        return HashUtil.sha256(rawToken);
    }
}