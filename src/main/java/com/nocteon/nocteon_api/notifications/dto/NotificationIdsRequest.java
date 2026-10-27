package com.nocteon.nocteon_api.notifications.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * A batch of notification ids for the bulk operations.
 *
 * <p>Ids that no longer exist (or belong to someone else) are ignored: a bulk
 * delete from an optimistic UI must not fail because another tab deleted one
 * row first. The cap keeps a single request bounded.
 */
@Getter
@Setter
public class NotificationIdsRequest {

    @NotEmpty(message = "{validation.notifications.ids.notEmpty}")
    @Size(max = 200, message = "{validation.notifications.ids.size}")
    private List<@NotNull Long> ids;
}
