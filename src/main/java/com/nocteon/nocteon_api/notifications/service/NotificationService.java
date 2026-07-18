package com.nocteon.nocteon_api.notifications.service;

import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.notifications.dto.NotificationResponse;
import com.nocteon.nocteon_api.notifications.entity.Notification;
import com.nocteon.nocteon_api.notifications.enums.NotificationType;
import com.nocteon.nocteon_api.notifications.event.OrderCreatedEvent;
import com.nocteon.nocteon_api.notifications.event.PaymentSucceededEvent;
import com.nocteon.nocteon_api.notifications.event.ReviewCreatedEvent;
import com.nocteon.nocteon_api.notifications.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("=== onOrderCreated LISTENER FIRED for order {} ===", event.orderId());
        try {
            Notification saved = notificationRepository.save(Notification.builder()
                    .type(NotificationType.ORDER_CREATED)
                    .title("New order received")
                    .message("Order #" + event.orderId() + " - " + event.totalAmount() + " EGP")
                    .link("/dashboard/orders/" + event.orderId())
                    .build());
            log.info("=== notification saved successfully with ID: {} ===", saved.getId());
        } catch (Exception e) {
            log.error("Failed to create notification for order {}", event.orderId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        try {
            notificationRepository.save(Notification.builder()
                    .type(NotificationType.PAYMENT_SUCCEEDED)
                    .title("Payment received")
                    .message("Order #" + event.orderId() + " - " + event.amount() + " EGP paid")
                    .link("/dashboard/orders/" + event.orderId())
                    .build());
        } catch (Exception e) {
            log.error("Failed to create notification for payment on order {}", event.orderId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReviewCreated(ReviewCreatedEvent event) {
        try {
            notificationRepository.save(Notification.builder()
                    .type(NotificationType.REVIEW_CREATED)
                    .title("Review Created")
                    .message("A new review " + event.reviewId() + " was submitted for product  #" + event.productSlug())
                    .link("/dashboard/reviews/" + event.reviewId())
                    .build());
        } catch (Exception e) {
            log.error("Failed to create notification for Review on product {}", event.reviewId(), e);
        }
    }

    public PageResponse<NotificationResponse> getNotifications(boolean unreadOnly, Pageable pageable) {
        var page = unreadOnly
                ? notificationRepository.findByIsReadFalseOrderByCreatedAtDesc(pageable)
                : notificationRepository.findAllByOrderByCreatedAtDesc(pageable);

        return PageResponse.of(page.map(this::toResponse));
    }

    public long getUnreadCount() {
        return notificationRepository.countByIsReadFalse();
    }

    @Transactional
    public void markAsRead(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead() {
        notificationRepository.markAllAsRead();
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .link(n.getLink())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
