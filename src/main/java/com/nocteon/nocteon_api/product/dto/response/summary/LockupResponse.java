
package com.nocteon.nocteon_api.product.dto.response.summary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LockupResponse {
    private String slug;
    private String name;
    private String description;

}