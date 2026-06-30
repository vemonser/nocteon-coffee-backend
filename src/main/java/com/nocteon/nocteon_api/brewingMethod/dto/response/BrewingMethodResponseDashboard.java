package com.nocteon.nocteon_api.brewingMethod.dto.response;

import java.util.List;

import com.nocteon.nocteon_api.common.dto.TranslationResponse;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class BrewingMethodResponseDashboard {
    private Long id;
    private String slug;
    private List<TranslationResponse> translations;

}
