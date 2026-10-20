package com.nocteon.nocteon_api.order.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.order.dto.request.OrderFilterRequest;
import com.nocteon.nocteon_api.order.dto.response.OrderItemResponse;
import com.nocteon.nocteon_api.order.dto.response.OrderResponse;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.common.exception.notFound.OrderNotFoundException;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.order.dto.response.OrderPaymentDetailsResponse;
import com.nocteon.nocteon_api.order.dto.response.OrderRefundResponse;
import com.nocteon.nocteon_api.payment.entity.Payment;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;
import com.nocteon.nocteon_api.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderQueryService {
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderRefundService orderRefundService;

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getUserOrders(UserPrincipal principal, BaseFilterRequest filter) {
        Page<Order> page = orderRepository.findByUserId(
                principal.getUserId(), filter.toPageable());
        return PageResponse.of(page.map(this::buildResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getAllOrders(OrderFilterRequest filter) {
        Page<Order> page = orderRepository.findAllWithFilters(
                filter.getStatus(),
                filter.getPaymentStatus(),
                filter.getPaymentMethod(),
                startOfDay(filter.getDateFrom()),
                startOfDayAfter(filter.getDateTo()),
                normaliseSearch(filter.getSearch()),
                filter.toPageable());
        return PageResponse.of(page.map(this::buildResponse));
    }

    /**
     * The first instant of a calendar day, in the store's own time zone.
     *
     * <p>The range is half-open: the first day at 00:00 and the day AFTER
     * the last one, so an order placed at 23:30 belongs to that day and the
     * range never depends on the reader's own clock.
     */
    static Instant startOfDay(LocalDate day) {
        if (day == null) {
            return ANY_TIME_FROM;
        }
        return day.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    /** The first instant of the day AFTER the given one. */
    static Instant startOfDayAfter(LocalDate day) {
        if (day == null) {
            return ANY_TIME_TO;
        }
        return day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    /**
     * The widest range the query accepts, used when the filter has no bound.
     *
     * <p>A parameter that appears in a "is null" test has no type there, and
     * PostgreSQL then cannot infer the type of the parameter in the
     * comparison next to it. So the bounds are never null: an absent filter
     * is a range that covers everything.
     */
    static final Instant ANY_TIME_FROM = Instant.parse("1970-01-01T00:00:00Z");
    static final Instant ANY_TIME_TO = Instant.parse("2100-01-01T00:00:00Z");

    /**
     * The search term as a ready-made LIKE pattern, lower-cased.
     *
     * <p>An empty term matches everything; a blank one must not become a
     * "%%" that the query reads as "contains nothing".
     */
    private static String normaliseSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return "%" + search.trim().toLowerCase() + "%";
    }

    /**
     * The payment of an order, in full: the transaction reference, when the
     * money arrived, the refunds and the net paid.
     *
     * <p>The NET PAID is the amount that came in minus the refunds. It is
     * null when the order is flagged as refunded but no refund amount is
     * recorded: a flag without an amount cannot be turned into a number, and
     * a number that is wrong is worse than one that is missing.
     */
    @Transactional(readOnly = true)
    public OrderPaymentDetailsResponse getPaymentDetails(Long id) {
        Order order = orderRepository.findById(id)
                        .orElseThrow(OrderNotFoundException::new);

        Payment payment = paymentRepository.findFirstByOrderIdOrderByAttemptNumberDesc(id).orElse(null);
        List<OrderRefundResponse> refunds = orderRefundService.getRefunds(id);

        BigDecimal refunded = refunds.stream()
                        .map(OrderRefundResponse::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal paid = payment != null ? payment.getAmount() : order.getTotalAmount();

        BigDecimal netPaid = null;
        if (order.getPaymentStatus() != PaymentStatus.REFUNDED || !refunds.isEmpty()) {
                netPaid = paid.subtract(refunded);
        }

        return OrderPaymentDetailsResponse.builder()
                        .method(order.getPaymentMethod() != null ? order.getPaymentMethod().name() : null)
                        .status(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null)
                        .transactionReference(payment != null ? payment.getProviderPaymentId() : null)
                        .paidAt(payment != null ? payment.getPaidAt() : null)
                        .amount(paid)
                        .netPaid(netPaid)
                        .refunds(refunds)
                        .build();
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new com.nocteon.nocteon_api.common.exception.notFound.OrderNotFoundException());
        return buildResponse(order);
    }

    OrderResponse buildResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> {
                    com.nocteon.nocteon_api.product.entity.Product product = item.getProductVariant().getProduct();
                    String productName = product.getTranslations().stream()
                            .filter(t -> "en".equals(t.getLanguage()))
                            .map(com.nocteon.nocteon_api.product.entity.ProductTranslation::getName)
                            .findFirst()
                            .orElse(product.getTranslations().isEmpty() ? null
                                    : product.getTranslations().iterator().next().getName());
                    String primaryImageUrl = product.getMedia().stream()
                            .filter(com.nocteon.nocteon_api.product.entity.ProductMedia::isPrimary)
                            .map(com.nocteon.nocteon_api.product.entity.ProductMedia::getUrl)
                            .findFirst()
                            .orElse(product.getMedia().stream()
                                    .sorted(java.util.Comparator.comparingInt(com.nocteon.nocteon_api.product.entity.ProductMedia::getSortOrder))
                                    .map(com.nocteon.nocteon_api.product.entity.ProductMedia::getUrl)
                                    .findFirst()
                                    .orElse(null));
                    return OrderItemResponse.builder()
                            .id(item.getId())
                            .variantId(item.getProductVariant().getId())
                            .sku(item.getProductVariant().getSku())
                            .productSlug(product.getSlug())
                            .productName(productName)
                            .primaryImageUrl(primaryImageUrl)
                            .quantity(item.getQuantity())
                            .unitPrice(item.getUnitPrice())
                            .totalPrice(item.getTotalPrice())
                            .build();
                })
                .toList();

        com.nocteon.nocteon_api.address.dto.response.AddressResponse addressResp = null;
        if (order.getAddress() != null) {
            addressResp = com.nocteon.nocteon_api.address.dto.response.AddressResponse.builder()
                    .id(order.getAddress().getId())
                    .fullName(order.getAddress().getFullName())
                    .phone(order.getAddress().getPhone())
                    .street(order.getAddress().getStreet())
                    .city(order.getAddress().getCity())
                    .state(order.getAddress().getState())
                    .country(order.getAddress().getCountry())
                    .postalCode(order.getAddress().getPostalCode())
                    .isDefault(order.getAddress().isDefault())
                    .build();
        }

        com.nocteon.nocteon_api.order.dto.response.OrderResponse.CustomerInfo customer = null;
        if (order.getUser() != null) {
            String name = order.getUser().getUsername();
            String avatar = null;
            try {
                if (order.getUser().getProfile() != null) {
                    String fn = order.getUser().getProfile().getFullName();
                    if (fn != null && !fn.isBlank()) name = fn;
                    avatar = order.getUser().getProfile().getAvatarUrl();
                }
            } catch (Exception ignored) {
                // profile may be uninitialized outside a session; fall back to username
            }
            customer = com.nocteon.nocteon_api.order.dto.response.OrderResponse.CustomerInfo.builder()
                    .id(order.getUser().getId())
                    .name(name)
                    .email(order.getUser().getEmail())
                    .avatar(avatar)
                    .build();
        }

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .paymentStatus(order.getPaymentStatus())
                .paymentMethod(order.getPaymentMethod())
                .shippingCost(order.getShippingCost())
                .discountAmount(order.getDiscountAmount())
                .notes(order.getNotes())
                .cancellationReason(order.getCancellationReason())
                .items(items)
                .createdAt(order.getCreatedAt())
                .address(addressResp)
                .customer(customer)
                .shipment(OrderShipmentService.toResponse(order))
                .build();
    }
}
