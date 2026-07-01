package com.nocteon.nocteon_api.roastLevel.dto.response;

import java.util.List;

import com.nocteon.nocteon_api.common.dto.TranslationResponse;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class DashboardRoastLevelResponse {
    private Long id;
    private String slug;
    private String color;
    private List<TranslationResponse> translations;

}
