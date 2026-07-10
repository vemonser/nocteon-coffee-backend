package com.nocteon.nocteon_api.mail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "application.brevo")
@Getter
@Setter
public class BrevoTemplateProperties {
    
    private Long orderShipped;
    private Long orderConfirmation;
    private Long paymentSuccess;
    private Long abandonedCart;
    private Long adminBroadcast;
}