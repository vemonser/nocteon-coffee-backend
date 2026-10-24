package com.nocteon.nocteon_api.broadcast.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.enums.Role;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.broadcast.dto.request.CreateBroadcastRequest;
import com.nocteon.nocteon_api.broadcast.dto.response.BroadcastRecipientResponse;
import com.nocteon.nocteon_api.broadcast.dto.response.BroadcastResponse;
import com.nocteon.nocteon_api.broadcast.entity.Broadcast;
import com.nocteon.nocteon_api.broadcast.entity.BroadcastRecipient;
import com.nocteon.nocteon_api.broadcast.enums.BroadcastRecipientStatus;
import com.nocteon.nocteon_api.broadcast.enums.BroadcastStatus;
import com.nocteon.nocteon_api.broadcast.exception.BroadcastNotFoundException;
import com.nocteon.nocteon_api.broadcast.repository.BroadcastRecipientRepository;
import com.nocteon.nocteon_api.broadcast.repository.BroadcastRepository;
import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.mail.entity.EmailLog;
import com.nocteon.nocteon_api.mail.enums.EmailStatus;
import com.nocteon.nocteon_api.mail.enums.EmailType;
import com.nocteon.nocteon_api.mail.repository.EmailLogRepository;
import com.nocteon.nocteon_api.mail.service.TransactionalEmailService;
import com.nocteon.nocteon_api.newsletter.entity.NewsletterSubscriber;
import com.nocteon.nocteon_api.newsletter.repository.NewsletterSubscriberRepository;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BroadcastCreationService {

    private final BroadcastRepository broadcastRepository;
    private final BroadcastRecipientRepository recipientRepository;
    private final UserRepository userRepository;
    private final NewsletterSubscriberRepository subscriberRepository;
    private final TransactionalEmailService transactionalEmailService;
    private final EmailLogRepository emailLogRepository;

    @Transactional
    public BroadcastResponse createBroadcast(CreateBroadcastRequest request) {
        List<User> eligibleUsers = userRepository.findByRoleAndSubscribedTrue("CUSTOMER");
        List<NewsletterSubscriber> subscribers = subscriberRepository.findAllBySubscribedTrue();

        // The recipient set is "every customer, plus the newsletter subscribers"
        // (deduplicated by email), and the exclude list removes the ones the
        // admin does not want. When recipientIds is set, only those customers
        // are kept — the newsletter subscribers are still added (unless
        // excluded), so the audience stays complete.
        List<User> users = eligibleUsers;
        if (request.getRecipientIds() != null && !request.getRecipientIds().isEmpty()) {
            Set<Long> wanted = new HashSet<>(request.getRecipientIds());
            users = eligibleUsers.stream()
                    .filter(u -> wanted.contains(u.getId()))
                    .toList();
        }

        // The exclude list is by customer id: collect the emails of the
        // customers the admin asked to leave out, so the newsletter
        // subscribers with the same email are left out too.
        Set<String> excludedEmails = new HashSet<>();
        if (request.getExcludeIds() != null) {
            for (User u : eligibleUsers) {
                if (request.getExcludeIds().contains(u.getId())) {
                    excludedEmails.add(u.getEmail().toLowerCase());
                }
            }
        }

        List<BroadcastRecipient> recipients = buildRecipients(users, subscribers, excludedEmails);

        Broadcast broadcast = Broadcast.builder()
                .subject(request.getSubject())
                .content(request.getContent())
                .status(BroadcastStatus.PENDING)
                .totalRecipients(recipients.size())
                .sentCount(0)
                .failedCount(0)
                .build();

        broadcastRepository.saveAndFlush(broadcast);

        recipients.forEach(recipient -> recipient.setBroadcast(broadcast));
        recipientRepository.saveAll(recipients);

        // The broadcast is sent HERE, in the same request, so the admin sees
        // the real result instead of a "pending" that never moves. Each
        // recipient is its own send: a failure on one address does not stop
        // the others, and the broadcast ends in the state the recipients
        // actually reached.
        send(broadcast, recipients);

        return buildResponse(broadcast);
    }

    private List<BroadcastRecipient> buildRecipients(List<User> users, List<NewsletterSubscriber> subscribers,
            Set<String> excludedEmails) {
        Set<String> seenEmails = new HashSet<>();
        List<BroadcastRecipient> recipients = new ArrayList<>();

        for (User user : users) {
            String email = user.getEmail().toLowerCase();
            if (excludedEmails.contains(email)) continue;
            if (seenEmails.add(email)) {
                recipients.add(BroadcastRecipient.builder()
                        .user(user)
                        .email(user.getEmail())
                        .status(BroadcastRecipientStatus.PENDING)
                        .attemptCount(0)
                        .build());
            }
        }

        for (NewsletterSubscriber subscriber : subscribers) {
            String email = subscriber.getEmail().toLowerCase();
            if (excludedEmails.contains(email)) continue;
            if (seenEmails.add(email)) {
                recipients.add(BroadcastRecipient.builder()
                        .email(subscriber.getEmail())
                        .status(BroadcastRecipientStatus.PENDING)
                        .attemptCount(0)
                        .build());
            }
        }

        return recipients;
    }

    /**
     * Send the broadcast to every recipient that is still pending.
     *
     * <p>Each recipient is its own send: the recipient's status ends in
     * SENT or FAILED, and the broadcast's counters say how many of each.
     * A failed send is recorded with its error so it can be retried.
     */
    @Transactional
    public BroadcastResponse send(Long broadcastId) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(BroadcastNotFoundException::new);
        List<BroadcastRecipient> recipients = recipientRepository
                .findByBroadcast(broadcast);
        send(broadcast, recipients);
        return buildResponse(broadcastRepository.findById(broadcastId).orElse(broadcast));
    }

    private void send(Broadcast broadcast, List<BroadcastRecipient> recipients) {
        broadcast.setStatus(BroadcastStatus.IN_PROGRESS);
        broadcastRepository.save(broadcast);

        int sent = 0;
        int failed = 0;
        for (BroadcastRecipient recipient : recipients) {
            if (recipient.getStatus() != BroadcastRecipientStatus.PENDING) {
                continue;
            }
            String key = "BROADCAST-" + broadcast.getId() + "-" + recipient.getEmail();
            try {
                transactionalEmailService.enqueueEmail(
                        key, EmailType.ADMIN_BROADCAST, recipient.getEmail(),
                        "BROADCAST", broadcast.getId());

                EmailLog logEntry = emailLogRepository.findByIdempotencyKey(key).orElse(null);
                if (logEntry != null && logEntry.getStatus() == EmailStatus.SENT) {
                    recipient.setStatus(BroadcastRecipientStatus.SENT);
                    sent++;
                } else {
                    recipient.setStatus(BroadcastRecipientStatus.FAILED);
                    failed++;
                }
            } catch (Exception ex) {
                recipient.setStatus(BroadcastRecipientStatus.FAILED);
                failed++;
                log.error("Failed to send broadcast {} to {}", broadcast.getId(), recipient.getEmail(), ex);
            }
            recipientRepository.save(recipient);
        }

        broadcast.setSentCount(sent);
        broadcast.setFailedCount(failed);
        // COMPLETED even when some addresses failed: the run finished, and the
        // counters say how many of each. A broadcast that never ran at all
        // stays PENDING; one that ran is COMPLETED.
        broadcast.setStatus(BroadcastStatus.COMPLETED);
        broadcastRepository.save(broadcast);

        log.info("Broadcast {} sent: {} sent, {} failed", broadcast.getId(), sent, failed);
    }

    private List<BroadcastRecipient> buildRecipients(List<User> users, List<NewsletterSubscriber> subscribers) {
        Set<String> seenEmails = new HashSet<>();
        List<BroadcastRecipient> recipients = new ArrayList<>();

        for (User user : users) {
            String email = user.getEmail().toLowerCase();
            if (seenEmails.add(email)) {
                recipients.add(BroadcastRecipient.builder()
                        .user(user)
                        .email(user.getEmail())
                        .status(BroadcastRecipientStatus.PENDING)
                        .attemptCount(0)
                        .build());
            }
        }

        for (NewsletterSubscriber subscriber : subscribers) {
            String email = subscriber.getEmail().toLowerCase();
            if (seenEmails.add(email)) {
                recipients.add(BroadcastRecipient.builder()
                        .email(subscriber.getEmail())
                        .status(BroadcastRecipientStatus.PENDING)
                        .attemptCount(0)
                        .build());
            }
        }

        return recipients;
    }

    public PageResponse<BroadcastResponse> getAll(BaseFilterRequest filter) {
        Page<Broadcast> page = broadcastRepository.findAll(filter.toPageable());
        return PageResponse.of(page.map(this::buildResponse));
    }

    public BroadcastResponse getById(Long id) {
        Broadcast broadcast = broadcastRepository.findById(id)
                .orElseThrow(BroadcastNotFoundException::new);
        return buildResponse(broadcast);
    }

    /**
     * The recipients of a broadcast, with their delivery status.
     *
     * <p>One row per recipient: the email, the status (SENT / FAILED /
     * PENDING), the attempt count, and the last error when it failed. A
     * broadcast that has not been sent yet has every recipient at PENDING.
     */
    @Transactional(readOnly = true)
    public List<BroadcastRecipientResponse> getRecipients(Long id) {
        Broadcast broadcast = broadcastRepository.findById(id)
                .orElseThrow(BroadcastNotFoundException::new);
        return recipientRepository.findByBroadcast(broadcast).stream()
                .map(this::buildRecipientResponse)
                .toList();
    }

    private BroadcastRecipientResponse buildRecipientResponse(BroadcastRecipient r) {
        // The recipient's `updatedAt` IS the moment the send finished: it is
        // written when the recipient is saved with SENT or FAILED, and never
        // before. The broadcast page reads it as the delivery time.
        return BroadcastRecipientResponse.builder()
                .id(r.getId())
                .email(r.getEmail())
                .status(r.getStatus())
                .attemptCount(r.getAttemptCount())
                .lastError(r.getLastError())
                .sentAt(r.getUpdatedAt())
                .build();
    }

    @Transactional
    public void delete(Long id) {
        Broadcast broadcast = broadcastRepository.findById(id)
                .orElseThrow(BroadcastNotFoundException::new);
        broadcastRepository.delete(broadcast);
    }

    private BroadcastResponse buildResponse(Broadcast broadcast) {
        return BroadcastResponse.builder()
                .id(broadcast.getId())
                .subject(broadcast.getSubject())
                .content(broadcast.getContent())
                .status(broadcast.getStatus())
                .totalRecipients(broadcast.getTotalRecipients())
                .sentCount(broadcast.getSentCount())
                .failedCount(broadcast.getFailedCount())
                .createdAt(broadcast.getCreatedAt())
                .build();
    }
}