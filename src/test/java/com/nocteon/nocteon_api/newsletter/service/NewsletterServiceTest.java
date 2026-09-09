package com.nocteon.nocteon_api.newsletter.service;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nocteon.nocteon_api.common.util.HashUtil;
import com.nocteon.nocteon_api.newsletter.entity.NewsletterSubscriber;
import com.nocteon.nocteon_api.newsletter.enums.SubscriptionResult;
import com.nocteon.nocteon_api.newsletter.exception.NewsletterSubscriberNotFoundException;
import com.nocteon.nocteon_api.newsletter.repository.NewsletterSubscriberRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsletterServiceTest {

    @Mock
    private NewsletterSubscriberRepository subscriberRepository;

    @InjectMocks
    private NewsletterService newsletterService;

    @Test
    void subscribe_savesNewSubscriberWithHashedToken() {
        when(subscriberRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.empty());

        SubscriptionResult result = newsletterService.subscribe(" TEST@example.com ");

        ArgumentCaptor<NewsletterSubscriber> captor = ArgumentCaptor.forClass(NewsletterSubscriber.class);
        verify(subscriberRepository).save(captor.capture());

        NewsletterSubscriber saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("test@example.com");
        assertThat(saved.isSubscribed()).isTrue();
        assertThat(saved.getTokenHash()).isNotBlank();
        assertThat(result).isEqualTo(SubscriptionResult.SUBSCRIBED);
    }

    @Test
    void subscribe_returnsAlreadySubscribedWhenAlreadySubscribed() {
        NewsletterSubscriber existing = NewsletterSubscriber.builder()
                .email("test@example.com")
                .tokenHash("existing-hash")
                .subscribed(true)
                .build();

        when(subscriberRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(existing));

        SubscriptionResult result = newsletterService.subscribe("test@example.com");

        assertThat(result).isEqualTo(SubscriptionResult.ALREADY_SUBSCRIBED);
        verify(subscriberRepository, never()).save(existing);
    }

    @Test
    void subscribe_reactivatesWhenPreviouslyUnsubscribed() {
        NewsletterSubscriber existing = NewsletterSubscriber.builder()
                .email("test@example.com")
                .tokenHash("old-hash")
                .subscribed(false)
                .build();

        when(subscriberRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(existing));

        SubscriptionResult result = newsletterService.subscribe("test@example.com");

        assertThat(result).isEqualTo(SubscriptionResult.RE_SUBSCRIBED);
        assertThat(existing.isSubscribed()).isTrue();
        assertThat(existing.getTokenHash()).isNotEqualTo("old-hash");
        verify(subscriberRepository).save(existing);
    }

    @Test
    void unsubscribe_marksSubscriberAsUnsubscribed() {
        NewsletterSubscriber existing = NewsletterSubscriber.builder()
                .email("test@example.com")
                .tokenHash(HashUtil.sha256("raw-token"))
                .subscribed(true)
                .build();

        when(subscriberRepository.findByTokenHash(HashUtil.sha256("raw-token")))
                .thenReturn(Optional.of(existing));

        newsletterService.unsubscribe("raw-token");

        assertThat(existing.isSubscribed()).isFalse();
        verify(subscriberRepository).save(existing);
    }

    @Test
    void unsubscribe_throwsWhenTokenUnknown() {
        when(subscriberRepository.findByTokenHash(HashUtil.sha256("unknown")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> newsletterService.unsubscribe("unknown"))
                .isInstanceOf(NewsletterSubscriberNotFoundException.class);
        verify(subscriberRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}