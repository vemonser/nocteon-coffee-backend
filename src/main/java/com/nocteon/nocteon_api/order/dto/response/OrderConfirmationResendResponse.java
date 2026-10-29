package com.nocteon.nocteon_api.order.dto.response;

import com.nocteon.nocteon_api.mail.enums.EmailStatus;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * The answer to "resend the confirmation".
 *
 * <p>The status travels so the dashboard can turn a failure into a warning
 * instead of a green toast: a message the provider refused is not a success,
 * and the row is queued for the retry worker rather than lost.
 */
@Getter
@Setter
@Builder
public class OrderConfirmationResendResponse {
    private Long orderId;
    private String recipientEmail;
    private EmailStatus emailStatus;
}
