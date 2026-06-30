package com.nocteon.nocteon_api.order.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
import com.nocteon.nocteon_api.order.dto.request.CreateOrderRequest;
import com.nocteon.nocteon_api.order.dto.request.OrderFilterRequest;
import com.nocteon.nocteon_api.order.dto.response.OrderPaymentResponse;
import com.nocteon.nocteon_api.order.dto.response.OrderResponse;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.order.service.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

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

//     // Moyasar Callback
//     @GetMapping("/orders/payment/callback")
//     public ResponseEntity<Void> paymentCallback(
//             @RequestParam String id,
//             @RequestParam Long orderId) {
//         orderService.handlePaymentCallback(id, orderId);
//         return ResponseEntity.ok().build();
//     }
// Paymob Webhook
        @PostMapping("/orders/payment/webhook")
        public ResponseEntity<Void> paymentWebhook(
                @RequestParam Map<String, String> params) {
        orderService.handlePaymentWebhook(params);
        return ResponseEntity.ok().build();
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