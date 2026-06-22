package com.nocteon.nocteon_api.farm.dto.request;

import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FarmFilterRequest extends BaseFilterRequest {
    private String search;
    private String originSlug;
}