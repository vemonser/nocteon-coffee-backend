package com.nocteon.nocteon_api.search.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum SearchResultType {
    ORDER("orders"),
    USER("users"),
    PRODUCT("products"),
    PROMO_CODE("promo-codes"),
    JOURNAL_POST("journal-posts"),
    CATEGORY("categories");

    private final String routeSegment;

    SearchResultType(String routeSegment) {
        this.routeSegment = routeSegment;
    }

    @JsonValue
    public String getRouteSegment() {
        return routeSegment;
    }
}
