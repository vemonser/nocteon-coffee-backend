package com.nocteon.nocteon_api.tastingNote.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class TastingNoteResponseDashboard {
    private Long id;
    private String slug;
    private List<TranslationResponseDashboard> translations;

}
