package com.nocteon.nocteon_api.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymobCallbackRequest {
    private String type;
    private PaymobTransactionObj obj;
}
