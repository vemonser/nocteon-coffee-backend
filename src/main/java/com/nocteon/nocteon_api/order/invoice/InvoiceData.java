package com.nocteon.nocteon_api.order.invoice;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.payment.enums.PaymentMethod;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;

/**
 * Everything an invoice prints, and nothing else.
 *
 * <p>A value object on purpose: the numbers come straight off the order
 * (the stored unit price and line total of the day it was placed), so a later
 * price change or a soft-deleted variant cannot rewrite an invoice that was
 * already sent. The renderer only draws.
 *
 * <p>{@code currency} is the store's currency code, printed in front of every
 * amount the same way the dashboard prints money.
 */
public record InvoiceData(
        InvoiceLabels labels,
        String storeName,
        String currency,
        String orderNumber,
        Instant placedAt,
        OrderStatus status,
        String customerName,
        String customerEmail,
        String customerPhone,
        List<String> addressLines,
        List<Line> lines,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal shipping,
        BigDecimal total,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        String transactionReference,
        Instant paidAt,
        Instant generatedAt) {

    /** One printed line: the day's name, the SKU, the count and the money. */
    public record Line(String name, String sku, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    /**
     * An amount as the dashboard writes it: currency code, a space, then the
     * value with two decimals and Latin digits ("EGP 1,250.00").
     *
     * <p>Never a bare number: an invoice whose totals carry no currency cannot
     * be reconciled against the store.
     */
    public String money(BigDecimal amount) {
        if (amount == null) {
            return currency + " 0.00";
        }
        DecimalFormat format = new DecimalFormat("#,##0.00",
                DecimalFormatSymbols.getInstance(Locale.UK));
        return currency + " " + format.format(amount);
    }
}
