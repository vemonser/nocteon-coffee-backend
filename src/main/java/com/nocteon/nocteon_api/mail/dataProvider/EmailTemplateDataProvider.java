package com.nocteon.nocteon_api.mail.dataProvider;

import java.util.Map;

import com.nocteon.nocteon_api.mail.enums.EmailType;

public interface EmailTemplateDataProvider {
    EmailType supports();
    Map<String, Object> buildParams(Long relatedEntityId);
}