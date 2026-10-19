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
    REVIEW_MODERATE("review:moderate"),
    REVIEW_UPDATE("review:update"),
    REVIEW_DELETE("review:delete"),
    
    SHIPPING_CREATE("shipping:create"),
    SHIPPING_READ("shipping:read"),
    SHIPPING_MODERATE("shipping:moderate"),
    SHIPPING_UPDATE("shipping:update"),
    SHIPPING_DELETE("shipping:delete"),
    // user
    USER_READ("user:read"),
    // Provisioning is a separate grant from editing. AdminUserController's create endpoint
    // required 'user:create' before this constant existed, and that string is not one any role
    // holds — so POST /api/dashboard/users returned 403 for ADMIN too, making it unreachable.
    USER_CREATE("user:create"),
    USER_UPDATE("user:update"),
    // Changing someone's role is separated from editing their profile on purpose.
    // AdminUserController's update route is gated on user:update, which MODERATOR holds, and the
    // request body carried an unbounded `role` field that UserService.adminUpdate applied without
    // restriction. A moderator could therefore PUT {"role":"ADMIN"} and grant themselves every
    // permission in the system, including user:delete and user:create, which MODERATOR
    // deliberately does not hold. Only ADMIN carries this one.
    USER_CHANGE_ROLE("user:change_role"),
    USER_DELETE("user:delete"),

    // address (self-service address book; split from user:read/update so that CUSTOMER can manage
    // its own addresses without gaining read access to /api/dashboard/users)
    ADDRESS_READ("address:read"),
    ADDRESS_UPDATE("address:update"),

    // dashboard
    SETTINGS_READ("settings:read"),
    SETTINGS_UPDATE("settings:update"),
    // dashboard
    PROMO_READ("promo:read"),
    PROMO_UPDATE("promo:update"),
    PROMO_DELETE("promo:delete"),
    PROMO_CREATE("promo:create"),
    
    // dashboard
    DASHBOARD_READ("dashboard:read"),
    DASHBOARD_UPDATE("dashboard:update"),
    
    // NOTIFICATION
    NOTIFICATION_READ("notification:read"),
    NOTIFICATION_UPDATE("notification:update"),
    NOTIFICATION_DELETE("notification:delete"),

    // Broadcast
    BROADCAST_CREATE("broadcast:create"),
    BROADCAST_READ("broadcast:read"),
    // BroadcastController's delete route asked for broadcast:delete before this constant existed.
    // hasAuthority compares raw strings, so that string failed closed: 403 for every role,
    // including ADMIN, which is granted Permission.values().
    BROADCAST_DELETE("broadcast:delete"),

    // Contact
    // The inbox's reply and mark-as-read routes asked for contact:update, and delete for
    // contact:delete, before either constant existed — so both failed closed with 403 for every
    // role. Like contact:read, these stay ADMIN-only: MODERATOR holds no contact permission.
    CONTACT_READ("contact:read"),
    CONTACT_UPDATE("contact:update"),
    CONTACT_DELETE("contact:delete"),

    // Cart & Wishlist
    // CART_MANAGE means "manage your own cart" and is deliberately held by CUSTOMER — the
    // storefront needs it for the basket button. It must never gate a dashboard endpoint:
    // DashboardCartService reads by id without an ownership check, so reusing it there let any
    // registered customer enumerate every other user's cart, including their email and full name.
    // CART_READ_ALL exists so that staff-wide cart visibility is an explicit, separate grant.
    CART_MANAGE("cart:manage"),
    CART_READ_ALL("cart:read_all"),
    WISHLIST_MANAGE("wishlist:manage"),
    AUDIT_READ("audit:read"),

    // AI
    // Rebuilding the vector index re-embeds the whole catalog, so it is not gated behind
    // product:update — only an admin pays that cost.
    AI_MANAGE("ai:manage");

    private final String permission;

    Permission(String permission) {
        this.permission = permission;
    }

    public String getPermission() {
        return permission;
    }
}