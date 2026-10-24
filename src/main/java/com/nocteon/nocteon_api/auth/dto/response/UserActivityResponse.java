package com.nocteon.nocteon_api.auth.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivityResponse {
    private long ordersCount;
    private BigDecimal lifetimeSpend;
    private Instant lastOrderAt;
}
