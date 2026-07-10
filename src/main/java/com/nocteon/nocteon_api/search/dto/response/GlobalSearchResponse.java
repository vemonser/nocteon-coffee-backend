package com.nocteon.nocteon_api.search.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GlobalSearchResponse {
    private List<SearchResultDto> results;
    private int totalCount;
}