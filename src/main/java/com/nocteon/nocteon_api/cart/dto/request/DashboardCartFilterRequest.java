package com.nocteon.nocteon_api.cart.dto.request;

import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DashboardCartFilterRequest extends BaseFilterRequest {
    private String search;
    private Boolean hasItems;
}
