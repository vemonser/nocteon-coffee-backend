package com.nocteon.nocteon_api.mail.dto;

import java.util.List;
import java.util.Map;

public record BrevoEmailRequest(
        BrevoSender sender,
        List<BrevoRecipient> to,
        Long templateId,
        Map<String, Object> params
) {}