package com.nocteon.nocteon_api.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymobDataObj {
    private String message;

    @JsonProperty("txn_response_code")
    private String txnResponseCode;
}
