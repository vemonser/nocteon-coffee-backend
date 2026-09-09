package com.nocteon.nocteon_api.newsletter.exception;

import org.springframework.http.HttpStatus;

import com.nocteon.nocteon_api.common.exception.BaseApiException;

public class NewsletterSubscriberNotFoundException extends BaseApiException {
    public NewsletterSubscriberNotFoundException() {
        super("error.newsletter.subscriber.notFound", HttpStatus.NOT_FOUND);
    }
}