package com.nocteon.nocteon_api.auth.enums;

import java.util.Set;

import com.nocteon.nocteon_api.common.enums.Permission;

public enum Role {

    ADMIN(Set.of(Permission.values())), 
    
    /** Content/community moderator: see, moderate, talk to customers. No identity,
     *  catalogue-write, money or settings grants — keep in step with V13. */
    MODERATOR(Set.of(
            Permission.DASHBOARD_READ,
            Permission.ORDER_READ,
            Permission.ORDER_UPDATE,
            Permission.CART_READ_ALL,
            Permission.REVIEW_READ,
            Permission.REVIEW_MODERATE,
            Permission.REVIEW_DELETE,
            Permission.CONTACT_READ,
            Permission.CONTACT_UPDATE,
            Permission.NOTIFICATION_READ,
            Permission.NOTIFICATION_UPDATE,
            Permission.PRODUCT_READ,
            Permission.CATEGORY_READ,
            Permission.ORIGIN_READ,
            Permission.FARM_READ,
            Permission.ROAST_LEVEL_READ,
            Permission.PROCESSING_METHOD_READ,
            Permission.COFFEE_VARIETY_READ,
            Permission.TASTING_NOTE_READ,
            Permission.BREWING_METHOD_READ,
            Permission.PAIRING_READ,
            Permission.AUDIT_READ
    )),

    CUSTOMER(Set.of(
            Permission.CATEGORY_READ,
            Permission.PRODUCT_READ,
            Permission.ORIGIN_READ,
            Permission.FARM_READ,
            Permission.ROAST_LEVEL_READ,
            Permission.PROCESSING_METHOD_READ,
            Permission.COFFEE_VARIETY_READ,
            Permission.TASTING_NOTE_READ,
            Permission.BREWING_METHOD_READ,
            Permission.PAIRING_READ,
            Permission.JOURNAL_READ,
            Permission.ORDER_CREATE, Permission.ORDER_READ,
            Permission.REVIEW_CREATE, Permission.REVIEW_READ, Permission.REVIEW_UPDATE,
            // Self-service address book. AddressController now gates /api/addresses on
            // address:read / address:update, which CUSTOMER alone holds, so a customer can
            // manage its own addresses without gaining read access to /api/dashboard/users
            // (which is gated on user:read and is not granted to CUSTOMER).
            Permission.ADDRESS_READ, Permission.ADDRESS_UPDATE,
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