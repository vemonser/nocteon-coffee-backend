package com.nocteon.nocteon_api.common.exception.invalid;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

/**
 * A contact message cannot be re-sent because it has no reply yet.
 *
 * <p>Re-sending is the escape hatch for a message whose email did not go
 * out. A message that was never answered has nothing to send.
 */
public class InvalidContactReplyException extends BaseApiException {
    public InvalidContactReplyException() {
        super("error.contactReply.none", HttpStatus.BAD_REQUEST);
    }
}
