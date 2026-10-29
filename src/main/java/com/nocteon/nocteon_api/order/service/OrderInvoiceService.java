package com.nocteon.nocteon_api.order.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.common.exception.notFound.OrderNotFoundException;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.entity.OrderItem;
import com.nocteon.nocteon_api.order.invoice.InvoiceData;
import com.nocteon.nocteon_api.order.invoice.InvoiceLabels;
import com.nocteon.nocteon_api.order.invoice.InvoicePdfRenderer;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.payment.entity.Payment;
import com.nocteon.nocteon_api.payment.repository.PaymentRepository;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.storeSettings.service.StoreSettingsService;

import lombok.RequiredArgsConstructor;

/**
 * The invoice of one order, as a PDF.
 *
 * <p>The document is built from what the ORDER stored — the unit price and the
 * line total of the day it was placed — so a later price change, a renamed
 * product or a trashed variant cannot rewrite an invoice that has already been
 * sent to a customer.
 *
 * <p><strong>Who may download it.</strong> The dashboard's order endpoints are
 * gated on {@code order:read}, which the CUSTOMER role also holds (a customer
 * reads their own orders through it). Serving an invoice on that authority
 * alone would let any logged-in customer download anyone's invoice, so the
 * check is narrower here: an admin/moderator (anyone who may CHANGE an order)
 * sees every invoice, and everyone else only their own — with the same 404 an
 * unknown id answers, so the response never confirms that someone else's order
 * exists. The resend endpoint needs no such rule: it is gated on
 * {@code order:update}, which customers never hold.
 */
@Service
@RequiredArgsConstructor
public class OrderInvoiceService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final StoreSettingsService storeSettingsService;
    private final InvoicePdfRenderer renderer;

    /** The order's invoice, ready to be written to the response. */
    public record InvoiceDocument(byte[] content, String filename) {
    }

    /**
     * Build the invoice of an order.
     *
     * @param orderId  the order
     * @param language {@code ar} for the Arabic document, anything else for English
     * @param principal who asks — decides whether this order may be read
     */
    @Transactional(readOnly = true)
    public InvoiceDocument invoice(Long orderId, String language, UserPrincipal principal) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(OrderNotFoundException::new);
        assertMayRead(order, principal);

        InvoiceData data = buildData(order, language);
        return new InvoiceDocument(renderer.render(data), "invoice-" + order.getOrderNumber() + ".pdf");
    }

    /**
     * The same object the PDF is drawn from, exposed for the tests and for any
     * future plain-text rendering: it is the answer to "what does the invoice
     * say", independent of how it is drawn.
     */
    @Transactional(readOnly = true)
    public InvoiceData dataOf(Long orderId, String language, UserPrincipal principal) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(OrderNotFoundException::new);
        assertMayRead(order, principal);
        return buildData(order, language);
    }

    // ─── building ────────────────────────────────────────────────────────────

    private InvoiceData buildData(Order order, String language) {
        InvoiceLabels labels = InvoiceLabels.of(language);
        String currency = storeSettingsService.get().getCurrency();
        Payment payment = paymentRepository.findFirstByOrderIdOrderByAttemptNumberDesc(order.getId()).orElse(null);

        List<InvoiceData.Line> lines = order.getItems().stream()
                .map(item -> new InvoiceData.Line(
                        productName(item, language),
                        item.getProductVariant() != null ? item.getProductVariant().getSku() : null,
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getTotalPrice()))
                .toList();

        BigDecimal subtotal = order.getItems().stream()
                .map(OrderItem::getTotalPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new InvoiceData(
                labels,
                "Nocteon",
                currency == null ? "EGP" : currency,
                order.getOrderNumber(),
                order.getCreatedAt(),
                order.getStatus(),
                customerName(order),
                order.getUser() != null ? order.getUser().getEmail() : null,
                order.getAddress() != null ? order.getAddress().getPhone() : null,
                addressLines(order),
                lines,
                subtotal,
                order.getDiscountAmount(),
                order.getShippingCost(),
                order.getTotalAmount(),
                order.getPaymentMethod(),
                order.getPaymentStatus(),
                payment != null ? payment.getProviderPaymentId() : null,
                payment != null ? payment.getPaidAt() : null,
                Instant.now());
    }

    /** The product name in the invoice's language, falling back to English. */
    private String productName(OrderItem item, String language) {
        if (item.getProductVariant() == null) {
            return "";
        }
        Product product = item.getProductVariant().getProduct();
        if (product == null || product.getTranslations() == null || product.getTranslations().isEmpty()) {
            return "";
        }
        String wanted = InvoiceLabels.isArabic(language) ? "ar" : "en";
        return product.getTranslations().stream()
                .filter(t -> wanted.equalsIgnoreCase(t.getLanguage()))
                .map(ProductTranslation::getName)
                .findFirst()
                .or(() -> product.getTranslations().stream()
                        .filter(t -> "en".equalsIgnoreCase(t.getLanguage()))
                        .map(ProductTranslation::getName)
                        .findFirst())
                .orElseGet(() -> product.getTranslations().iterator().next().getName());
    }

    /**
     * Who the invoice is addressed to.
     *
     * <p>The account's full name when it has one, then the name on the shipping
     * address (what the courier will read), then the username — never blank, and
     * never "null".
     */
    private String customerName(Order order) {
        if (order.getUser() != null && order.getUser().getProfile() != null) {
            String fullName = order.getUser().getProfile().getFullName();
            if (fullName != null && !fullName.isBlank()) {
                return fullName;
            }
        }
        if (order.getAddress() != null && order.getAddress().getFullName() != null
                && !order.getAddress().getFullName().isBlank()) {
            return order.getAddress().getFullName();
        }
        if (order.getUser() == null || order.getUser().getUsername() == null) {
            return "";
        }
        return order.getUser().getUsername();
    }

    /** The postal address, one printed line per part, empty parts dropped. */
    private List<String> addressLines(Order order) {
        List<String> lines = new ArrayList<>();
        if (order.getAddress() == null) {
            return lines;
        }
        addIfPresent(lines, order.getAddress().getStreet());
        addIfPresent(lines, join(order.getAddress().getCity(), order.getAddress().getState()));
        addIfPresent(lines, join(order.getAddress().getCountry(), order.getAddress().getPostalCode()));
        return lines;
    }

    private void addIfPresent(List<String> lines, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(value);
        }
    }

    private String join(String first, String second) {
        boolean hasFirst = first != null && !first.isBlank();
        boolean hasSecond = second != null && !second.isBlank();
        if (hasFirst && hasSecond) {
            return first + ", " + second;
        }
        if (hasFirst) {
            return first;
        }
        return hasSecond ? second : null;
    }

    // ─── access ──────────────────────────────────────────────────────────────

    /**
     * The order's own customer, or anyone who may change orders.
     *
     * <p>{@code order:update} is the marker, not the role name: it is granted
     * to the console roles and never to CUSTOMER, and a custom staff role that
     * holds it is staff by definition. A read-only staff role that does not hold
     * it still sees the order page (that is gated on {@code order:read}) but not
     * someone else's invoice — a narrower answer is the safe one for a document
     * that carries a customer's name, address and money.
     */
    private void assertMayRead(Order order, UserPrincipal principal) {
        if (principal == null) {
            throw new OrderNotFoundException();
        }
        boolean mayChangeOrders = principal.getPermissions().contains("order:update");
        if (mayChangeOrders) {
            return;
        }
        Long ownerId = order.getUser() != null ? order.getUser().getId() : null;
        if (!Objects.equals(ownerId, principal.getUserId())) {
            throw new OrderNotFoundException();
        }
    }
}
