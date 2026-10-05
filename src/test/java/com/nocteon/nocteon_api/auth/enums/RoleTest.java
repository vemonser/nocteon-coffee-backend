package com.nocteon.nocteon_api.auth.enums;

import org.junit.jupiter.api.Test;

import com.nocteon.nocteon_api.common.enums.Permission;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins which roles may administer the AI index.
 *
 * <p>Two things are asserted here rather than left to reading the enum. {@code ADMIN} is declared as
 * {@code Set.of(Permission.values())}, so it gains every permission added to the enum — including
 * one added months later by somebody who never thought about roles — and {@code MODERATOR} and
 * {@code CUSTOMER} are declared by hand, so a permission reaches them only if it is typed into
 * their set on purpose.
 *
 * <p>That is why {@code ai:manage} is asserted to be absent from both. Rebuilding the vector index
 * re-embeds the entire catalog, which is a cost only an admin should be able to trigger on demand;
 * {@code product:update} is deliberately not enough, and the two moderators who can already edit
 * products are not implicitly trusted with it.
 */
class RoleTest {

    @Test
    void adminCanAdministerTheAiIndex() {
        assertThat(Role.ADMIN.getPermissions()).contains(Permission.AI_MANAGE);
    }

    @Test
    void aModeratorCannotAdministerTheAiIndex() {
        assertThat(Role.MODERATOR.getPermissions()).doesNotContain(Permission.AI_MANAGE);
    }

    @Test
    void aCustomerCannotAdministerTheAiIndex() {
        assertThat(Role.CUSTOMER.getPermissions()).doesNotContain(Permission.AI_MANAGE);
    }

    /**
     * {@code @PreAuthorize("hasAuthority('ai:manage')")} is matched against the permission string,
     * not the enum name, so a typo here would surface as a 403 for a correctly-permissioned admin
     * rather than as a compile error.
     */
    @Test
    void theAiPermissionStringMatchesTheEndpointAuthority() {
        assertThat(Permission.AI_MANAGE.getPermission()).isEqualTo("ai:manage");
    }

    /** The blanket ADMIN grant must stay blanket, or new permissions ship unreachable. */
    @Test
    void adminHoldsEveryDeclaredPermission() {
        assertThat(Role.ADMIN.getPermissions())
                .containsExactlyInAnyOrder(Permission.values());
    }
}