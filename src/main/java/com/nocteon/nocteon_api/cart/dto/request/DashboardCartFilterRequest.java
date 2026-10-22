package com.nocteon.nocteon_api.cart.dto.request;

import java.time.Instant;

import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DashboardCartFilterRequest extends BaseFilterRequest {
    private String search;
    private Boolean hasItems;
    private String status;
    private Instant from;
    private Instant to;
}