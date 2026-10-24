package com.nocteon.nocteon_api.notifications.exception;

import org.springframework.http.HttpStatus;
import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class NotificationNotFoundException extends BaseApiException {
    public NotificationNotFoundException() {
        super("error.notification.not.found", HttpStatus.NOT_FOUND);
    }
}
