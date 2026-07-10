package com.nocteon.nocteon_api.product.dto.request;



import com.nocteon.nocteon_api.product.enums.MediaType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductMediaRequest {
    private Long id; // null = new upload (needs a matching file), non-null = existing media to keep/update

    private MediaType type;

    private String altText;

    private Integer sortOrder;

    private Boolean isPrimary;

}