package com.nocteon.nocteon_api.tastingNote.dto.response;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class TastingNoteResponse {
    private Long id;
    private String slug;
    private String name;
    private Instant createdAt;

    
}
