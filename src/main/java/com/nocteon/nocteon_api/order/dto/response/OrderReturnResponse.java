package com.nocteon.nocteon_api.order.dto.response;

import java.time.Instant;
import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * A return, as the order page draws it.
 *
 * <p>{@code items} carry the product name and the SKU so the section can say
 * "3 × Ethiopia Yirgacheffe" without a second request per line, and
 * {@code orderItemId} so a line can be matched to the order's own table.
 *
 * <p>{@code refundId} is present only when the return is linked to a refund the
 * store recorded; the page shows the amount from its own refund list.
 */
@Getter
@Setter
@Builder
public class OrderReturnResponse {

    private Long id;

    private Long orderId;

    private String status;

    private String reason;

    private String requestedBy;

    private String decidedBy;

    private Instant decidedAt;

    private Long refundId;

    private Instant createdAt;

    private Instant updatedAt;

    private List<Item> items;

    /** One returned line, with what it is, so the page needs no second read. */
    @Getter
    @Setter
    @Builder
    public static class Item {

        private Long id;

        private Long orderItemId;

        /** The variant the units went back to — the restock target. */
        private Long variantId;

        private String sku;

        private String productName;

        private int quantity;

        private String note;
    }
}
