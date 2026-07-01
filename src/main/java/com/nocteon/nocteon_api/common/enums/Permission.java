package com.nocteon.nocteon_api.common.enums;

public enum Permission {

    // Category
    CATEGORY_CREATE("category:create"),
    CATEGORY_READ("category:read"),
    CATEGORY_UPDATE("category:update"),
    CATEGORY_DELETE("category:delete"),

    // Product
    PRODUCT_CREATE("product:create"),
    PRODUCT_READ("product:read"),
    PRODUCT_UPDATE("product:update"),
    PRODUCT_DELETE("product:delete"),

    // Origin
    ORIGIN_CREATE("origin:create"),
    ORIGIN_READ("origin:read"),
    ORIGIN_UPDATE("origin:update"),
    ORIGIN_DELETE("origin:delete"),

    // Farm
    FARM_CREATE("farm:create"),
    FARM_READ("farm:read"),
    FARM_UPDATE("farm:update"),
    FARM_DELETE("farm:delete"),

    // Roast Level
    ROAST_LEVEL_CREATE("roast_level:create"),
    ROAST_LEVEL_READ("roast_level:read"),
    ROAST_LEVEL_UPDATE("roast_level:update"),
    ROAST_LEVEL_DELETE("roast_level:delete"),

    // Processing Method
    PROCESSING_METHOD_CREATE("processing_method:create"),
    PROCESSING_METHOD_READ("processing_method:read"),
    PROCESSING_METHOD_UPDATE("processing_method:update"),
    PROCESSING_METHOD_DELETE("processing_method:delete"),

    // Coffee Variety
    COFFEE_VARIETY_CREATE("coffee_variety:create"),
    COFFEE_VARIETY_READ("coffee_variety:read"),
    COFFEE_VARIETY_UPDATE("coffee_variety:update"),
    COFFEE_VARIETY_DELETE("coffee_variety:delete"),

    // Tasting Note
    TASTING_NOTE_CREATE("tasting_note:create"),
    TASTING_NOTE_READ("tasting_note:read"),
    TASTING_NOTE_UPDATE("tasting_note:update"),
    TASTING_NOTE_DELETE("tasting_note:delete"),

    // Brewing Method
    BREWING_METHOD_CREATE("brewing_method:create"),
    BREWING_METHOD_READ("brewing_method:read"),
    BREWING_METHOD_UPDATE("brewing_method:update"),
    BREWING_METHOD_DELETE("brewing_method:delete"),

    // Pairing
    PAIRING_CREATE("pairing:create"),
    PAIRING_READ("pairing:read"),
    PAIRING_UPDATE("pairing:update"),
    PAIRING_DELETE("pairing:delete"),

    // Journal
    JOURNAL_CREATE("journal:create"),
    JOURNAL_READ("journal:read"),
    JOURNAL_UPDATE("journal:update"),
    JOURNAL_DELETE("journal:delete"),

    // Order
    ORDER_CREATE("order:create"),
    ORDER_READ("order:read"),
    ORDER_UPDATE("order:update"),
    ORDER_DELETE("order:delete"),

    // Review
    REVIEW_CREATE("review:create"),
    REVIEW_READ("review:read"),
    REVIEW_UPDATE("review:update"),
    REVIEW_DELETE("review:delete"),

    // User
    USER_READ("user:read"),
    USER_UPDATE("user:update"),
    USER_DELETE("user:delete"),

    // Cart & Wishlist
    CART_MANAGE("cart:manage"),
    WISHLIST_MANAGE("wishlist:manage");

    private final String permission;

    Permission(String permission) {
        this.permission = permission;
    }

    public String getPermission() {
        return permission;
    }
}