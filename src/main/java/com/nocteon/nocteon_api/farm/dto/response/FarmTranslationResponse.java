package com.nocteon.nocteon_api.farm.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class FarmTranslationResponse {
    private String language;
    private String name;
    private String description;
    private String country;

}
