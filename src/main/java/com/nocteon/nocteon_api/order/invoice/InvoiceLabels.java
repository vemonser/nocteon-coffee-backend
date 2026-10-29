package com.nocteon.nocteon_api.order.invoice;

import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.payment.enums.PaymentMethod;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;

/**
 * The words on the invoice, in the two languages the dashboard speaks.
 *
 * <p>Not the message bundles: this document is drawn with a PDF library, not
 * rendered by Spring, so the labels travel as a value object the renderer can
 * read. The <em>status</em> words are the same Arabic the dashboard uses
 * ({@code orders.statuses} / {@code orders.paymentStatuses} in
 * {@code ar.json}) so a customer reading the PDF and an admin reading the order
 * page see one vocabulary, not two.
 */
public record InvoiceLabels(
        boolean rightToLeft,
        String invoice,
        String billedTo,
        String orderDetails,
        String orderNumber,
        String placedOn,
        String status,
        String item,
        String quantity,
        String unitPrice,
        String amount,
        String subtotal,
        String discount,
        String shipping,
        String total,
        String payment,
        String method,
        String paymentStatus,
        String reference,
        String paidOn,
        String notRecorded,
        String generatedOn,
        String noItems) {

    private static final InvoiceLabels EN = new InvoiceLabels(
            false,
            "Invoice",
            "Billed to",
            "Order details",
            "Order number",
            "Placed on",
            "Status",
            "Item",
            "Qty",
            "Unit price",
            "Amount",
            "Subtotal",
            "Discount",
            "Shipping",
            "Total",
            "Payment",
            "Method",
            "Payment status",
            "Reference",
            "Paid on",
            "Not recorded",
            "Generated on",
            "This order has no items.");

    private static final InvoiceLabels AR = new InvoiceLabels(
            true,
            "فاتورة",
            "بيانات العميل",
            "تفاصيل الطلب",
            "رقم الطلب",
            "تاريخ الطلب",
            "الحالة",
            "الصنف",
            "الكمية",
            "سعر الوحدة",
            "الإجمالي",
            "المجموع الفرعي",
            "الخصم",
            "الشحن",
            "الإجمالي",
            "الدفع",
            "طريقة الدفع",
            "حالة الدفع",
            "مرجع العملية",
            "تاريخ الدفع",
            "غير مسجل",
            "تاريخ الإصدار",
            "لا توجد أصناف في هذا الطلب.");

    /** The labels for a language tag, defaulting to English. */
    public static InvoiceLabels of(String language) {
        return isArabic(language) ? AR : EN;
    }

    /** Whether the tag names Arabic — the invoice is drawn right to left. */
    public static boolean isArabic(String language) {
        return language != null && language.trim().toLowerCase().startsWith("ar");
    }

    /** The order status, in the invoice's language. */
    public String orderStatus(OrderStatus status) {
        if (status == null) {
            return "";
        }
        if (!rightToLeft) {
            return switch (status) {
                case PENDING -> "Pending";
                case CONFIRMED -> "Confirmed";
                case PROCESSING -> "Processing";
                case SHIPPED -> "Shipped";
                case DELIVERED -> "Delivered";
                case CANCELLED -> "Cancelled";
            };
        }
        return switch (status) {
            case PENDING -> "قيد الانتظار";
            case CONFIRMED -> "مؤكد";
            case PROCESSING -> "قيد المعالجة";
            case SHIPPED -> "تم الشحن";
            case DELIVERED -> "تم التوصيل";
            case CANCELLED -> "ملغى";
        };
    }

    /** The payment status, in the invoice's language. */
    public String paymentStatus(PaymentStatus status) {
        if (status == null) {
            return "";
        }
        if (!rightToLeft) {
            return switch (status) {
                case PENDING -> "Pending";
                case PAID -> "Paid";
                case FAILED -> "Failed";
                case REFUNDED -> "Refunded";
                case NOT_COLLECTED -> "Not collected";
            };
        }
        return switch (status) {
            case PENDING -> "قيد الانتظار";
            case PAID -> "مدفوع";
            case FAILED -> "فشل الدفع";
            case REFUNDED -> "تم الاسترداد";
            case NOT_COLLECTED -> "لم يُحصَّل";
        };
    }

    /** The payment method, in the invoice's language. */
    public String paymentMethod(PaymentMethod method) {
        if (method == null) {
            return "";
        }
        if (!rightToLeft) {
            return switch (method) {
                case ONLINE -> "Online payment";
                case CASH_ON_DELIVERY -> "Cash on delivery";
            };
        }
        return switch (method) {
            case ONLINE -> "الدفع الإلكتروني";
            case CASH_ON_DELIVERY -> "الدفع عند الاستلام";
        };
    }

    /**
     * A date and time the way the dashboard writes them: day, month, year, time
     * — with LATIN digits in both languages.
     *
     * <p>{@code Locale("ar")} formats numbers with Arabic-Indic digits, which
     * the dashboard does not use anywhere (its shared formatters emit Latin
     * digits in Arabic too). A PDF that switches numeral system between the two
     * languages would print order numbers and totals that cannot be read back
     * into the console, so the digits stay Latin and only the month names
     * translate.
     */
    public String dateTime(java.time.Instant instant) {
        if (instant == null) {
            return "";
        }
        java.time.ZonedDateTime local = instant.atZone(java.time.ZoneId.systemDefault());
        String day = String.format("%02d", local.getDayOfMonth());
        String month = rightToLeft
                ? ARABIC_MONTHS[local.getMonthValue() - 1]
                : local.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.UK);
        String time = String.format("%02d:%02d", local.getHour(), local.getMinute());
        return day + " " + month + " " + local.getYear() + (rightToLeft ? "، " : ", ") + time;
    }

    private static final String[] ARABIC_MONTHS = {
            "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
            "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
    };
}
