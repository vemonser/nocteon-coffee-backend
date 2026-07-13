package com.nocteon.nocteon_api.farm.dto.response;

import java.time.Instant;
import java.util.List;



import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class DashboardFarmResponse {
    private Long id;
    private String originSlug;
    private String slug;
    private String imageUrl;
    private Instant createdAt;
    private List<FarmTranslationResponse> translations;

}
