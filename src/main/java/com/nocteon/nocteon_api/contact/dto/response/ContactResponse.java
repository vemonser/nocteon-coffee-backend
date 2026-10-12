package com.nocteon.nocteon_api.contact.dto.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactResponse {
    private Long id;
    private String name;
    private String email;
    private String subject;
    private String message;
    private boolean isRead;
    private boolean isReplied;
    private String replyContent;
    private Instant createdAt;
}
