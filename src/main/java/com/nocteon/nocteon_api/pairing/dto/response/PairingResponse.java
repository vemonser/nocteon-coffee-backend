package com.nocteon.nocteon_api.pairing.dto.response;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PairingResponse {
    private Long id;
    private String slug;
    private String name;
    private String description;
    private String imageUrl;
    private Instant createdAt;

}