package com.nocteon.nocteon_api.userActivity.dto;

import com.nocteon.nocteon_api.userActivity.enums.DeviceType;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DeviceBreakdownDto {
    private DeviceType deviceType;
    private long count;
}