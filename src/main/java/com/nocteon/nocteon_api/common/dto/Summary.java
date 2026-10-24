package com.nocteon.nocteon_api.common.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Summary {

    private long total;
    private long admins;
    private long customers;
    private long blocked;
    private long unverified;

    public Summary() {}

    public Summary(long total, long admins, long customers, long blocked, long unverified) {
        this.total = total;
        this.admins = admins;
        this.customers = customers;
        this.blocked = blocked;
        this.unverified = unverified;
    }
}