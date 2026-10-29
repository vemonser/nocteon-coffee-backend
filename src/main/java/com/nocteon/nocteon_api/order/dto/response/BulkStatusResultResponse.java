package com.nocteon.nocteon_api.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What happened to ONE order inside a bulk status change (FE-18b).
 *
 * <p>Every id the caller sent comes back with exactly one of these, in the
 * order it was sent. A refusal is not an error of the call: the other rows
 * still ran, so the answer carries {@code success=false} and the server's own
 * sentence in {@code message} — the same sentence the single endpoint would
 * have answered as the body of its 409/404.
 *
 * <p>{@code orderNumber} is null only when the id does not exist (there is no
 * order to name); the dashboard already knows the number of the row it sent.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkStatusResultResponse {
    private Long id;
    private String orderNumber;
    private boolean success;
    private String message;
}
