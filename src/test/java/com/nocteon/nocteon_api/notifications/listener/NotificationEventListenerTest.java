package com.nocteon.nocteon_api.notifications.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.service.RoleService;
import com.nocteon.nocteon_api.notifications.event.ContactCreatedEvent;
import com.nocteon.nocteon_api.notifications.event.OrderCreatedEvent;
import com.nocteon.nocteon_api.notifications.event.UserRegisteredEvent;
import com.nocteon.nocteon_api.notifications.service.NotificationService;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.review.repository.ReviewRepository;

/**
 * The fan-out must reach the stream, which means it must go through the single
 * creation path ({@link NotificationService#createAndPublish}). A direct repository
 * insert here would create the row but no realtime event, and the bell would fall
 * back to its minute poll — the exact delay FE-23 removes.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleService roleService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @InjectMocks
    private NotificationEventListener listener;

    private static User user(long id, String role) {
        return User.builder()
                .id(id)
                .username("user" + id)
                .email("user" + id + "@example.test")
                .role(role)
                .build();
    }

    @Test
    void contactMessageFansOutThroughTheSingleCreationPath() {
        when(userRepository.findByIsActiveTrue())
                .thenReturn(List.of(user(5L, "ADMIN"), user(6L, "CUSTOMER")));
        when(roleService.getPermissions("ADMIN")).thenReturn(Set.of("contact:read"));
        when(roleService.getPermissions("CUSTOMER")).thenReturn(Set.of("product:read"));

        listener.onContactCreated(new ContactCreatedEvent(888L, "Subject", "Sender"));

        ArgumentCaptor<String> params = ArgumentCaptor.forClass(String.class);
        verify(notificationService)
                .createAndPublish(eq(5L), eq("contact.message"), params.capture(), eq("contact"), eq(888L));
        assertThat(params.getValue()).contains("\"subject\":\"Subject\"", "\"senderName\":\"Sender\"");
        // No permission, no notification — and no second path to the repository.
        verify(notificationService, never())
                .createAndPublish(eq(6L), anyString(), anyString(), anyString(), any());
        verifyNoInteractions(reviewRepository);
    }

    @Test
    void orderNotificationCarriesTheStoredOrder() {
        Order order = Order.builder().id(1234L).orderNumber("ORD-1234").build();
        when(orderRepository.findById(1234L)).thenReturn(java.util.Optional.of(order));
        when(userRepository.findByIsActiveTrue()).thenReturn(List.of(user(5L, "ADMIN")));
        when(roleService.getPermissions("ADMIN")).thenReturn(Set.of("order:read"));

        listener.onOrderCreated(new OrderCreatedEvent(1234L, new BigDecimal("1240.00")));

        ArgumentCaptor<String> params = ArgumentCaptor.forClass(String.class);
        verify(notificationService)
                .createAndPublish(eq(5L), eq("order.new"), params.capture(), eq("order"), eq(1234L));
        assertThat(params.getValue()).contains("\"orderNumber\":\"ORD-1234\"");
    }

    @Test
    void theActorIsNeverNotified() {
        when(userRepository.findByIsActiveTrue())
                .thenReturn(List.of(user(5L, "ADMIN"), user(7L, "ADMIN")));
        when(roleService.getPermissions("ADMIN")).thenReturn(Set.of("user:read"));

        listener.onUserRegistered(new UserRegisteredEvent(7L, "New Person"));

        verify(notificationService).createAndPublish(eq(5L), eq("user.new"), anyString(), eq("user"), eq(7L));
        verify(notificationService, never())
                .createAndPublish(eq(7L), anyString(), anyString(), anyString(), any());
    }

    @Test
    void anEmptyFanOutTouchesNothing() {
        when(userRepository.findByIsActiveTrue()).thenReturn(List.of());

        listener.onOrderCreated(new OrderCreatedEvent(1234L, BigDecimal.TEN));

        verifyNoInteractions(notificationService, reviewRepository);
    }
}
