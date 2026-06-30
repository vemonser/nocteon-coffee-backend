package com.nocteon.nocteon_api.roastProfile.dto.response;

import java.util.List;

import com.nocteon.nocteon_api.common.dto.TranslationResponse;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class DashboardRoastProfileResponse {
    private Long id;
    private String slug;
    private List<TranslationResponse> translations;

}
