package com.nocteon.nocteon_api.payment.dto.request;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymobSourceData {
    private String type;

    @JsonProperty("sub_type")
    private String subType;

    private String pan;
}
