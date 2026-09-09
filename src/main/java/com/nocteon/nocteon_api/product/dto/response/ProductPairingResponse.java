package com.nocteon.nocteon_api.product.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ProductPairingResponse {
    private String pairingSlug;
    private String pairingName;
    private String pairingImage;

}
