package com.nocteon.nocteon_api.notifications.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.auth.service.RoleService;
import com.nocteon.nocteon_api.cart.repository.CartRepository;
import com.nocteon.nocteon_api.contact.repository.ContactMessageRepository;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.review.entity.ReviewStatus;
import com.nocteon.nocteon_api.review.repository.ReviewRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NavCountService {

    private final RoleService roleService;
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ReviewRepository reviewRepository;
    private final ContactMessageRepository contactMessageRepository;

    @Transactional(readOnly = true)
    public Map<String, Long> countsFor(UserPrincipal principal) {
        var perms = roleService.getPermissions(principal.getUser().getRole());
        Map<String, Long> result = new LinkedHashMap<>();
        if (perms.contains("order:read")) {
            result.put("openOrders", orderRepository.countByStatusIn(
                    List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.SHIPPED)));
        }
        if (perms.contains("cart:read_all")) {
            result.put("abandonedCarts", cartRepository.countByStatus("ABANDONED"));
        }
        if (perms.contains("review:read")) {
            Long pending = reviewRepository.countByDeletedAtIsNullAndStatus(ReviewStatus.PENDING);
            result.put("pendingReviews", pending == null ? 0L : pending);
        }
        if (perms.contains("contact:read")) {
            Long unread = contactMessageRepository.countByIsReadFalse();
            result.put("unreadMessages", unread == null ? 0L : unread);
        }
        return result;
    }
}
