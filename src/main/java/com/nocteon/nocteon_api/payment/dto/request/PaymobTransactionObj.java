package com.nocteon.nocteon_api.payment.dto.request;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymobTransactionObj {

    private Long id;

    private boolean success;

    @JsonProperty("amount_cents")
    private Long amountCents;

    private String currency;

    @JsonProperty("is_refunded")
    private boolean isRefunded;

    @JsonProperty("is_voided")
    private boolean isVoided;

    @JsonProperty("is_3d_secure")
    private boolean is3dSecure;

    @JsonProperty("is_auth")
    private boolean isAuth;

    @JsonProperty("is_capture")
    private boolean isCapture;

    @JsonProperty("is_standalone_payment")
    private boolean isStandalonePayment;

    private boolean pending;

    @JsonProperty("has_parent_transaction")
    private boolean hasParentTransaction;

    @JsonProperty("integration_id")
    private Long integrationId;

    private String owner;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("error_occured")
    private boolean errorOccured;

    @JsonProperty("data")
    private PaymobDataObj data;

    private PaymobOrderObj order;

    @JsonProperty("source_data")
    private PaymobSourceData sourceData;
}
