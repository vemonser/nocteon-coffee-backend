package com.nocteon.nocteon_api.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** An internal note about an order. */
@Getter
@Setter
public class CreateOrderNoteRequest {

        @NotBlank(message = "{validation.note.notBlank}")
        @Size(max = 2000, message = "{validation.note.size}")
        private String body;

        /**
         * Whether the note is internal.
         *
         * <p>Always true from the dashboard: a note that is not internal is a
         * message to the customer, which is a different feature.
         */
        private boolean internal = true;
}
