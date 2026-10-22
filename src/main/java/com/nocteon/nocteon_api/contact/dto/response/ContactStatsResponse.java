package com.nocteon.nocteon_api.contact.dto.response;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactStatsResponse {
    private long total;
    private long newCount;
    private long repliedCount;
    private long archivedCount;
    private long unreadCount;
}