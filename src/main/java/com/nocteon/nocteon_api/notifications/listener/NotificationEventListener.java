package com.nocteon.nocteon_api.notifications.listener;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.service.RoleService;
import com.nocteon.nocteon_api.notifications.event.ContactCreatedEvent;
import com.nocteon.nocteon_api.notifications.event.OrderCreatedEvent;
import com.nocteon.nocteon_api.notifications.event.PaymentFailedEvent;
import com.nocteon.nocteon_api.notifications.event.ReviewCreatedEvent;
import com.nocteon.nocteon_api.notifications.event.StockChangedEvent;
import com.nocteon.nocteon_api.notifications.event.UserRegisteredEvent;
import com.nocteon.nocteon_api.notifications.service.NotificationService;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.review.entity.Review;
import com.nocteon.nocteon_api.review.repository.ReviewRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final RoleService roleService;
    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Async("notificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onOrderCreated(OrderCreatedEvent event) {
        try {
            Order order = orderRepository.findById(event.orderId()).orElse(null);
            if (order == null) return;
            String customerName = order.getUser() != null ? order.getUser().getUsername() : "customer";
            Map<String, Object> params = new HashMap<>();
            params.put("orderNumber", order.getOrderNumber());
            params.put("customerName", customerName);
            params.put("total", order.getTotalAmount());
            fanOut("order.new", "order:read", params, "order", order.getId(), null);
        } catch (Exception e) {
            log.error("Notification fan-out failed for order {}", event.orderId(), e);
        }
    }

    @Async("notificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onPaymentSucceeded(com.nocteon.nocteon_api.notifications.event.PaymentSucceededEvent event) {
        try {
            Order order = orderRepository.findById(event.orderId()).orElse(null);
            if (order == null) return;
            String customerName = order.getUser() != null ? order.getUser().getUsername() : "customer";
            Map<String, Object> params = new HashMap<>();
            params.put("orderNumber", order.getOrderNumber());
            params.put("customerName", customerName);
            params.put("total", event.amount() != null ? event.amount() : order.getTotalAmount());
            fanOut("order.new", "order:read", params, "order", order.getId(), null);
        } catch (Exception e) {
            log.error("Notification fan-out failed for payment on order {}", event.orderId(), e);
        }
    }

    @Async("notificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onPaymentFailed(PaymentFailedEvent event) {
        try {
            Order order = orderRepository.findById(event.orderId()).orElse(null);
            if (order == null) return;
            String customerName = order.getUser() != null ? order.getUser().getUsername() : "customer";
            Map<String, Object> params = new HashMap<>();
            params.put("orderNumber", order.getOrderNumber());
            params.put("customerName", customerName);
            params.put("amount", event.amount());
            fanOut("payment.failed", "order:read", params, "order", order.getId(), null);
        } catch (Exception e) {
            log.error("Notification fan-out failed for failed payment on order {}", event.orderId(), e);
        }
    }

    @Async("notificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onReviewCreated(ReviewCreatedEvent event) {
        try {
            Review review = reviewRepository.findById(event.reviewId()).orElse(null);
            if (review == null || review.getStatus() != com.nocteon.nocteon_api.review.entity.ReviewStatus.PENDING) return;
            String productName = review.getProduct() != null ? review.getProduct().getSlug() : "product";
            String reviewerName = review.getUser() != null ? review.getUser().getUsername() : "reviewer";
            Map<String, Object> params = new HashMap<>();
            params.put("productName", productName);
            params.put("rating", review.getRating());
            params.put("reviewerName", reviewerName);
            Long actor = review.getUser() != null ? review.getUser().getId() : null;
            fanOut("review.pending", "review:moderate", params, "review", review.getId(), actor);
        } catch (Exception e) {
            log.error("Notification fan-out failed for review {}", event.reviewId(), e);
        }
    }

    @Async("notificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onContactCreated(ContactCreatedEvent event) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("subject", event.subject());
            params.put("senderName", event.senderName());
            fanOut("contact.message", "contact:read", params, "contact", event.messageId(), null);
        } catch (Exception e) {
            log.error("Notification fan-out failed for contact {}", event.messageId(), e);
        }
    }

    @Async("notificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onStockChanged(StockChangedEvent event) {
        try {
            if (!(event.oldStock() > event.threshold() && event.newStock() <= event.threshold())) return;
            Map<String, Object> params = new HashMap<>();
            params.put("productName", event.productName());
            params.put("variantLabel", event.variantLabel());
            params.put("stock", event.newStock());
            fanOut("stock.low", "product:read", params, "product_variant", event.variantId(), null);
        } catch (Exception e) {
            log.error("Notification fan-out failed for variant {}", event.variantId(), e);
        }
    }

    @Async("notificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onUserRegistered(UserRegisteredEvent event) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("fullName", event.fullName());
            fanOut("user.new", "user:read", params, "user", event.userId(), event.userId());
        } catch (Exception e) {
            log.error("Notification fan-out failed for user {}", event.userId(), e);
        }
    }

    private void fanOut(String type, String permission, Map<String, Object> params, String entityType, Long entityId, Long actorUserId) {
        String paramsJson;
        try {
            paramsJson = objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            paramsJson = "{}";
        }
        for (var user : userRepository.findByIsActiveTrue()) {
            if (actorUserId != null && actorUserId.equals(user.getId())) continue;
            if (!roleService.getPermissions(user.getRole()).contains(permission)) continue;
            // The single creation path: inserts (idempotently) and pushes to the
            // recipient's live stream. Never insert here directly — that would be a
            // second creation path with no realtime push.
            notificationService.createAndPublish(user.getId(), type, paramsJson, entityType, entityId);
        }
    }
}
