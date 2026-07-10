package com.nocteon.nocteon_api.mail.dataProvider;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.mail.enums.EmailType;

@Service
public class EmailTemplateDataResolver {

    private final Map<EmailType, EmailTemplateDataProvider> providers;

    public EmailTemplateDataResolver(List<EmailTemplateDataProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(EmailTemplateDataProvider::supports, p -> p));
    }

    public Map<String, Object> resolve(EmailType type, Long relatedEntityId) {
        EmailTemplateDataProvider provider = providers.get(type);
        if (provider == null) {
            throw new IllegalStateException("No data provider registered for type: " + type);
        }
        return provider.buildParams(relatedEntityId);
    }
}