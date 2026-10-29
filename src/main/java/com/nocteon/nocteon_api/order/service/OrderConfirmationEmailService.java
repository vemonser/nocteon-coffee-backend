package com.nocteon.nocteon_api.order.service;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.common.exception.notFound.OrderNotFoundException;
import com.nocteon.nocteon_api.mail.entity.EmailLog;
import com.nocteon.nocteon_api.mail.enums.EmailStatus;
import com.nocteon.nocteon_api.mail.enums.EmailType;
import com.nocteon.nocteon_api.mail.repository.EmailLogRepository;
import com.nocteon.nocteon_api.mail.service.TransactionalEmailService;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.exception.OrderConfirmationNotSendableException;
import com.nocteon.nocteon_api.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Re-send the order confirmation e-mail on request.
 *
 * <p>Reuses the plumbing that already sends the confirmation when the order is
 * placed: the same {@link EmailType}, the same provider template, the same
 * {@code email_log} table and retry worker. Nothing about mail is
 * re-implemented here — this class decides <em>which</em> address, writes the
 * log row, and turns the outcome into a sentence the dashboard shows.
 *
 * <p><strong>One row per resend.</strong> The automatic confirmation is keyed
 * {@code ORDER-<id>-CONFIRMATION}, and that key is unique by design (a second
 * attempt must not queue a second copy of the same news). A resend is a
 * different fact — support pressed a button, today, because the customer says
 * they never got it — so it gets its own row and its own key, and
 * {@code email_log} stays an audit trail of every send attempt rather than a
 * single mutable slot.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderConfirmationEmailService {

    private static final String RELATED_ENTITY = "ORDER";

    private final OrderRepository orderRepository;
    private final EmailLogRepository emailLogRepository;
    private final TransactionalEmailService transactionalEmailService;
    private final MessageSource messageSource;

    /** What happened, in the words the toast shows. */
    public record ResendOutcome(Long orderId, String recipientEmail, EmailStatus status, String message) {
    }

    /**
     * Send the confirmation again, now.
     *
     * @param orderId the order
     * @return the status the log row ended in, and the backend's own message
     */
    @Transactional
    public ResendOutcome resendConfirmation(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(OrderNotFoundException::new);

        String recipient = order.getUser() != null ? order.getUser().getEmail() : null;
        if (recipient == null || recipient.isBlank()) {
            throw new OrderConfirmationNotSendableException();
        }

        long previous = emailLogRepository.countByRelatedEntityTypeAndRelatedEntityIdAndEmailType(
                RELATED_ENTITY, orderId, EmailType.ORDER_CONFIRMATION);
        String key = "ORDER-" + orderId + "-CONFIRMATION-RESEND-" + (previous + 1);

        transactionalEmailService.enqueueEmail(key, EmailType.ORDER_CONFIRMATION, recipient, RELATED_ENTITY, orderId);

        // The send is attempted inline, so the row already carries the answer.
        EmailStatus status = emailLogRepository.findByIdempotencyKey(key)
                .map(EmailLog::getStatus)
                .orElse(EmailStatus.PENDING);

        log.info("Order {} confirmation resent to {} (status {})", orderId, recipient, status);

        return new ResendOutcome(orderId, recipient, status, message(status, recipient));
    }

    /**
     * The sentence shown to the admin.
     *
     * <p>Localized server-side: this text is shown verbatim (the dashboard lets
     * the backend's message win over its own copy), so an English sentence in an
     * Arabic console would be the one visible string that never got translated.
     */
    private String message(EmailStatus status, String recipient) {
        Locale locale = LocaleContextHolder.getLocale();
        String key = switch (status) {
            case SENT -> "order.confirmation.resent";
            case FAILED, PENDING -> "order.confirmation.resentQueued";
            case DEAD -> "order.confirmation.resentDead";
        };
        return messageSource.getMessage(key, new Object[] { recipient }, key, locale);
    }
}
