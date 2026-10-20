package com.nocteon.nocteon_api.order.controller;

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
import org.springframework.validation.annotation.Validated;

import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.ratelimit.RateLimiterService;
import com.nocteon.nocteon_api.order.dto.request.CreateOrderNoteRequest;
import com.nocteon.nocteon_api.order.dto.request.CreateOrderRequest;
import com.nocteon.nocteon_api.order.dto.request.CreateRefundRequest;
import com.nocteon.nocteon_api.order.dto.request.OrderFilterRequest;
import com.nocteon.nocteon_api.order.dto.request.UpdateOrderStatusRequest;
import com.nocteon.nocteon_api.order.dto.request.UpdateShipmentRequest;
import com.nocteon.nocteon_api.order.dto.response.OrderNoteResponse;
import com.nocteon.nocteon_api.order.dto.response.OrderPaymentDetailsResponse;
import com.nocteon.nocteon_api.order.dto.response.OrderPaymentResponse;
import com.nocteon.nocteon_api.order.dto.response.OrderRefundResponse;
import com.nocteon.nocteon_api.order.dto.response.OrderResponse;
import com.nocteon.nocteon_api.order.dto.response.OrderShipmentResponse;
import com.nocteon.nocteon_api.order.dto.response.OrderStatsResponse;
import com.nocteon.nocteon_api.order.dto.response.OrderStatusHistoryResponse;
import com.nocteon.nocteon_api.order.entity.OrderRefund;
import com.nocteon.nocteon_api.payment.dto.request.PaymobCallbackRequest;
import com.nocteon.nocteon_api.order.entity.OrderStatusHistory;
import com.nocteon.nocteon_api.order.service.OrderNoteService;
import com.nocteon.nocteon_api.order.service.OrderQueryService;
import com.nocteon.nocteon_api.order.service.OrderRefundService;
import com.nocteon.nocteon_api.order.service.OrderService;
import com.nocteon.nocteon_api.order.service.OrderShipmentService;
import com.nocteon.nocteon_api.order.service.OrderStatsService;
import com.nocteon.nocteon_api.order.service.OrderStatusService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api")
@Validated
@RequiredArgsConstructor
@Slf4j
public class OrderController {

        private final OrderService orderService;
        private final OrderQueryService orderQueryService;
        private final OrderStatusService orderStatusService;
        private final OrderNoteService orderNoteService;
        private final OrderShipmentService orderShipmentService;
        private final OrderRefundService orderRefundService;
        private final OrderStatsService orderStatsService;
        private final RateLimiterService rateLimiterService;

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

        @PostMapping("/orders/{orderId}/payment/retry")
        @PreAuthorize("hasAuthority('order:update')")
        public ResponseEntity<ApiResponse<OrderPaymentResponse>> retryPayment(
                        @PathVariable Long orderId,
                        @AuthenticationPrincipal UserPrincipal principal) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                orderService.retryPayment(orderId, principal),
                                                "Payment link regenerated"));
        }

        @PostMapping("/orders/payment/webhook")
        public ResponseEntity<ApiResponse<Void>> paymentWebhook(
                        @RequestParam String hmac,
                        @RequestBody PaymobCallbackRequest request,
                        HttpServletRequest servletRequest) {

                String clientIp = extractClientIp(servletRequest);

                if (!rateLimiterService.tryConsumeWebhook(clientIp)) {
                        log.warn("Webhook rate limit exceeded");
                        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
                }

                orderService.handlePaymentWebhook(hmac, request);
                return ResponseEntity.ok().build();
        }

        private String extractClientIp(HttpServletRequest request) {
                String ip = request.getHeader("X-Real-IP");
                if (ip != null) {
                        return ip;
                }
                return request.getRemoteAddr();
        }

        @GetMapping("/orders/me")
        @PreAuthorize("hasAuthority('order:read')")
        public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getMyOrders(
                        @Valid
                        @ModelAttribute BaseFilterRequest filter,
                        @AuthenticationPrincipal UserPrincipal principal) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                orderQueryService.getUserOrders(principal, filter),
                                                "Orders retrieved"));
        }

        /**
         * The orders of the dashboard, with EVERY filter the list offers.
         *
         * <p>The filters are the same ones the counts endpoint takes, so the
         * cards and the rows always describe the same set.
         */
        @GetMapping("/dashboard/orders")
        @PreAuthorize("hasAuthority('order:read')")
        public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getAllOrders(
                        @Valid @ModelAttribute OrderFilterRequest filter) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                orderQueryService.getAllOrders(filter),
                                                "Orders retrieved"));
        }

        /**
         * The counts of the filtered set.
         *
         * <p>What the cards behind the list show, so a card can never
         * contradict the rows below it.
         */
        @GetMapping("/dashboard/orders/stats")
        @PreAuthorize("hasAuthority('order:read')")
        public ResponseEntity<ApiResponse<OrderStatsResponse>> getOrderStats(
                        @Valid @ModelAttribute OrderFilterRequest filter) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                orderStatsService.getStats(filter),
                                "Order statistics retrieved"));
        }

        @GetMapping("/dashboard/orders/{id}")
        @PreAuthorize("hasAuthority('order:read')")
        public ResponseEntity<ApiResponse<OrderResponse>> getById(@PathVariable Long id) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                orderQueryService.getById(id),
                                "Order retrieved"));
        }

        /**
         * The status history of an order, oldest first.
         *
         * <p>The chain of the order: placed, confirmed, shipped, delivered —
         * with who moved it and why.
         */
        @GetMapping("/dashboard/orders/{id}/history")
        @PreAuthorize("hasAuthority('order:read')")
        public ResponseEntity<ApiResponse<List<OrderStatusHistoryResponse>>> getHistory(@PathVariable Long id) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                orderStatusService.getHistory(id).stream()
                                                .map(OrderController::toHistoryResponse)
                                                .toList(),
                                "Order history retrieved"));
        }

        /**
         * Change the status of an order.
         *
         * <p>The transition is validated against the state machine, and the
         * history row is written in the same transaction.
         */
        @PutMapping("/dashboard/orders/{id}/status")
        @PreAuthorize("hasAuthority('order:update')")
        public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
                        @PathVariable Long id,
                        @Valid @RequestBody UpdateOrderStatusRequest request,
                        @AuthenticationPrincipal UserPrincipal principal) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                orderStatusService.updateStatus(id, request, actorOf(principal)),
                                "Order status updated"));
        }

        /** The internal notes of an order, newest first. */
        @GetMapping("/dashboard/orders/{id}/notes")
        @PreAuthorize("hasAuthority('order:read')")
        public ResponseEntity<ApiResponse<List<OrderNoteResponse>>> getNotes(@PathVariable Long id) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                orderNoteService.getNotes(id).stream()
                                                .map(OrderController::toNoteResponse)
                                                .toList(),
                                "Order notes retrieved"));
        }

        /** Add an internal note. */
        @PostMapping("/dashboard/orders/{id}/notes")
        @PreAuthorize("hasAuthority('order:update')")
        public ResponseEntity<ApiResponse<OrderNoteResponse>> addNote(
                        @PathVariable Long id,
                        @Valid @RequestBody CreateOrderNoteRequest request,
                        @AuthenticationPrincipal UserPrincipal principal) {
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success(
                                                toNoteResponse(
                                                                orderNoteService.addNote(id, request, actorOf(principal))),
                                                "Order note added"));
        }

        /** The shipment of an order. */
        @GetMapping("/dashboard/orders/{id}/shipment")
        @PreAuthorize("hasAuthority('order:read')")
        public ResponseEntity<ApiResponse<OrderShipmentResponse>> getShipment(@PathVariable Long id) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                orderShipmentService.getShipment(id),
                                "Order shipment retrieved"));
        }

        /** Record (or change) the shipment of an order. */
        @PutMapping("/dashboard/orders/{id}/shipment")
        @PreAuthorize("hasAuthority('order:update')")
        public ResponseEntity<ApiResponse<OrderShipmentResponse>> updateShipment(
                        @PathVariable Long id,
                        @Valid @RequestBody UpdateShipmentRequest request) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                orderShipmentService.updateShipment(id, request),
                                "Order shipment updated"));
        }

        /** The payment of an order, with the refunds and the net paid. */
        @GetMapping("/dashboard/orders/{id}/payment")
        @PreAuthorize("hasAuthority('order:read')")
        public ResponseEntity<ApiResponse<OrderPaymentDetailsResponse>> getPayment(@PathVariable Long id) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                orderQueryService.getPaymentDetails(id),
                                "Order payment retrieved"));
        }

        /**
         * Record a refund.
         *
         * <p>The money is NOT returned by this call: the gateway has no refund
         * wired in, so the refund is recorded and the order's payment status
         * becomes REFUNDED. The row says the refund is MANUAL until a gateway
         * reference is written.
         */
        @PostMapping("/dashboard/orders/{id}/refund")
        @PreAuthorize("hasAuthority('order:update')")
        public ResponseEntity<ApiResponse<OrderRefundResponse>> refund(
                        @PathVariable Long id,
                        @Valid @RequestBody CreateRefundRequest request,
                        @AuthenticationPrincipal UserPrincipal principal) {
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success(
                                                orderRefundService.refund(id, request, actorOf(principal)),
                                                "Order refund recorded"));
        }

        /** The refunds of an order, oldest first. */
        @GetMapping("/dashboard/orders/{id}/refunds")
        @PreAuthorize("hasAuthority('order:read')")
        public ResponseEntity<ApiResponse<List<OrderRefundResponse>>> getRefunds(@PathVariable Long id) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                orderRefundService.getRefunds(id),
                                "Order refunds retrieved"));
        }

        // ─── mapping ───────────────────────────────────────────────────────────

        /** Who did it: the e-mail of the admin, or the system. */
        private static String actorOf(UserPrincipal principal) {
                if (principal == null || principal.getUser() == null) {
                        return "system";
                }
                return principal.getUser().getEmail();
        }

        private static OrderStatusHistoryResponse toHistoryResponse(OrderStatusHistory row) {
                return OrderStatusHistoryResponse.builder()
                                .id(row.getId())
                                .orderId(row.getOrder() != null ? row.getOrder().getId() : null)
                                .fromStatus(row.getFromStatus())
                                .toStatus(row.getToStatus())
                                .note(row.getNote())
                                .changedBy(row.getChangedBy())
                                .notifyCustomer(row.isNotifyCustomer())
                                .createdAt(row.getCreatedAt())
                                .build();
        }

        private static OrderNoteResponse toNoteResponse(
                        com.nocteon.nocteon_api.order.entity.OrderNote note) {
                return OrderNoteResponse.builder()
                                .id(note.getId())
                                .orderId(note.getOrder() != null ? note.getOrder().getId() : null)
                                .author(note.getAuthor())
                                .body(note.getBody())
                                .internal(note.isInternal())
                                .createdAt(note.getCreatedAt())
                                .build();
        }
}
