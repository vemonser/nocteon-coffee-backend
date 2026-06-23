package com.nocteon.nocteon_api.journal.dto.request;

import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JournalFilterRequest extends BaseFilterRequest {
    private String search;
    private String categorySlug;
    private Boolean featured;
}