package com.nocteon.nocteon_api.broadcast.dto.request;

import java.util.List;

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

    /**
     * The customer ids to send to. When empty, the broadcast goes to every
     * subscribed customer. When set, only the named customers receive it.
     */
    private List<Long> recipientIds;

    /**
     * The customer ids to leave out. Applied on top of the recipient set:
     * a customer named here never receives the broadcast, even when
     * {@code recipientIds} is empty (which means "everyone").
     */
    private List<Long> excludeIds;
}