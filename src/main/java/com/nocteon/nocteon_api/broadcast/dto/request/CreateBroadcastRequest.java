package com.nocteon.nocteon_api.broadcast.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateBroadcastRequest {
    @NotBlank(message = "{validation.subject.notBlank}")
    @Size(max = 255)
    private String subject;

    @NotBlank(message = "{validation.content.notBlank}")
    private String content;
}