package com.nocteon.nocteon_api.common.dto;

import java.util.List;

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
public class Summary {

    private long total;
    private long admins;
    private long customers;
    private long blocked;
    private long unverified;
    private List<RoleCount> byRole;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleCount {
        private String role;
        private long userCount;
    }
}
