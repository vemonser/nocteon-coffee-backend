package com.nocteon.nocteon_api.contact.dto.response;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ContactResponse {

    private Long id;
    private String name;
    private String email;
    private String subject;
    private String message;
    private Instant createdAt;
}