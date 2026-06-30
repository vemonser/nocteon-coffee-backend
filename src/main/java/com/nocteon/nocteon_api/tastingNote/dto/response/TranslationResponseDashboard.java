package com.nocteon.nocteon_api.tastingNote.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TranslationResponseDashboard {
    private String language;
    private String name;
 }