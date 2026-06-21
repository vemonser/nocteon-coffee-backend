package com.nocteon.nocteon_api.auth.enums;

import java.util.Set;

import com.nocteon.nocteon_api.common.enums.Permission;

public enum Role {

    ADMIN(Set.of(Permission.values())), 
    
    MODERATOR(Set.of(
            Permission.CATEGORY_READ, Permission.CATEGORY_UPDATE,
            Permission.PRODUCT_READ, Permission.PRODUCT_UPDATE,
            Permission.ORIGIN_READ, Permission.ORIGIN_UPDATE,
            Permission.FARM_READ, Permission.FARM_UPDATE,
            Permission.ROAST_PROFILE_READ, Permission.ROAST_PROFILE_UPDATE,
            Permission.PROCESSING_METHOD_READ, Permission.PROCESSING_METHOD_UPDATE,
            Permission.COFFEE_VARIETY_READ, Permission.COFFEE_VARIETY_UPDATE,
            Permission.TASTING_NOTE_READ, Permission.TASTING_NOTE_UPDATE,
            Permission.BREWING_METHOD_READ, Permission.BREWING_METHOD_UPDATE,
            Permission.PAIRING_READ, Permission.PAIRING_UPDATE,
            Permission.JOURNAL_CREATE, Permission.JOURNAL_READ, Permission.JOURNAL_UPDATE,
            Permission.ORDER_CREATE, Permission.ORDER_READ, Permission.ORDER_UPDATE,
            Permission.REVIEW_CREATE, Permission.REVIEW_READ,
            Permission.REVIEW_UPDATE, Permission.REVIEW_DELETE,
            Permission.USER_READ, Permission.USER_UPDATE,
            Permission.CART_MANAGE, Permission.WISHLIST_MANAGE
    )),

    CUSTOMER(Set.of(
            Permission.CATEGORY_READ,
            Permission.PRODUCT_READ,
            Permission.ORIGIN_READ,
            Permission.FARM_READ,
            Permission.ROAST_PROFILE_READ,
            Permission.PROCESSING_METHOD_READ,
            Permission.COFFEE_VARIETY_READ,
            Permission.TASTING_NOTE_READ,
            Permission.BREWING_METHOD_READ,
            Permission.PAIRING_READ,
            Permission.JOURNAL_READ,
            Permission.ORDER_CREATE, Permission.ORDER_READ,
            Permission.REVIEW_CREATE, Permission.REVIEW_READ, Permission.REVIEW_UPDATE,
            Permission.USER_READ, Permission.USER_UPDATE,
            Permission.CART_MANAGE, Permission.WISHLIST_MANAGE
    ));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }
}