package com.nocteon.nocteon_api.search.dto.response;

import com.nocteon.nocteon_api.search.enums.SearchResultType;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SearchResultDto {
    private SearchResultType type;
    private Long id;
    private String title;       
    private String subtitle;    
    private String identifier;  
}