package com.nocteon.nocteon_api.order.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.ratelimit.RateLimiterService;
import com.nocteon.nocteon_api.order.dto.request.CreateOrderRequest;
import com.nocteon.nocteon_api.order.dto.request.OrderFilterRequest;
import com.nocteon.nocteon_api.order.dto.request.UpdateOrderStatusRequest;
import com.nocteon.nocteon_api.order.dto.response.OrderPaymentResponse;
import com.nocteon.nocteon_api.order.dto.response.OrderResponse;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.order.service.OrderService;
import com.nocteon.nocteon_api.order.service.OrderStatusService;
import com.nocteon.nocteon_api.payment.dto.request.PaymobCallbackRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

        private final OrderService orderService;
        private final RateLimiterService rateLimiterService;
        private final OrderStatusService orderStatusService;

        @PostMapping("/orders")
        @PreAuthorize("hasAuthority('order:create')")
        public ResponseEntity<ApiResponse<OrderPaymentResponse>> createOrder(
                        @Valid @RequestBody CreateOrderRequest request,
                        @AuthenticationPrincipal UserPrincipal principal) {
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success(
                                                orderService.createOrder(request, principal),
                                                "Order created"));
        }

        // @GetMapping("/orders/payment/callback")
        // public ResponseEntity<Void> paymentCallback(
        // @RequestParam String id,
        // @RequestParam Long orderId) {
        // orderService.handlePaymentCallback(id, orderId);
        // return ResponseEntity.ok().build();
        // }
        // Paymob Webhook
        @PatchMapping("/dashboard/orders/{id}/status")
        @PreAuthorize("hasAuthority('orders:update')")
        public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
                        @PathVariable Long id,
                        @RequestBody @Valid UpdateOrderStatusRequest request) {
                return ResponseEntity.ok(
                                ApiResponse.success(orderStatusService.updateStatus(id, request.getStatus()),
                                                "Order status updated"));
        }

        @PostMapping("/orders/{orderId}/payment/retry")
        @PreAuthorize("hasAuthority('order:create')")
        public ResponseEntity<ApiResponse<OrderPaymentResponse>> retryPayment(
                        @PathVariable Long orderId,
                        @AuthenticationPrincipal UserPrincipal principal) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                orderService.retryPayment(orderId, principal),
                                                "Payment link regenerated"));
        }

        @PostMapping("/orders/payment/webhook")
        public ResponseEntity<Void> paymentWebhook(
                        @RequestParam String hmac,
                        @RequestBody PaymobCallbackRequest payload,
                        HttpServletRequest request) {

                String clientIp = extractClientIp(request);

                if (!rateLimiterService.tryConsumeWebhook(clientIp)) {
                        log.warn("Webhook rate limit exceeded for IP: {}", clientIp);
                        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
                }

                orderService.handlePaymentWebhook(hmac, payload);
                return ResponseEntity.ok().build();
        }

        private String extractClientIp(HttpServletRequest request) {
                String forwardedFor = request.getHeader("X-Forwarded-For");
                if (forwardedFor != null && !forwardedFor.isEmpty()) {
                        // لو فيه أكتر من IP مفصولين بفاصلة، الأول هو الأصلي
                        return forwardedFor.split(",")[0].trim();
                }
                return request.getRemoteAddr();
        }

        @GetMapping("/orders/me")
        @PreAuthorize("hasAuthority('order:read')")
        public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getMyOrders(
                        @ModelAttribute BaseFilterRequest filter,
                        @AuthenticationPrincipal UserPrincipal principal) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                orderService.getUserOrders(principal, filter),
                                                "Orders retrieved"));
        }

        @GetMapping("/dashboard/orders")
        @PreAuthorize("hasAuthority('order:read')")
        public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getAllOrders(
                        @ModelAttribute OrderFilterRequest filter) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                orderService.getAllOrders(filter),
                                                "Orders retrieved"));
        }

        @PutMapping("/dashboard/orders/{id}/status")
        @PreAuthorize("hasAuthority('order:update')")
        public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
                        @PathVariable Long id,
                        @RequestParam OrderStatus status) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                orderService.updateStatus(id, status),
                                                "Order status updated"));
        }
}