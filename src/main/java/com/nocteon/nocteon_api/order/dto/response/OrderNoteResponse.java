package com.nocteon.nocteon_api.order.dto.response;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/** An internal note about an order. */
@Getter
@Setter
@Builder
public class OrderNoteResponse {

        private Long id;

        private Long orderId;

        private String author;

        private String body;

        private boolean internal;

        private Instant createdAt;
}
