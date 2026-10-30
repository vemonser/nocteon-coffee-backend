package com.nocteon.nocteon_api.common.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.common.exception.invalid.EntityInUseException;
import com.nocteon.nocteon_api.common.exception.invalid.NotInTrashException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

/**
 * The two rules every soft-deletable row obeys, in one place.
 *
 * <p><b>Rule 1 — a referenced row cannot be trashed.</b> The owner's rule is "I cannot put
 * something in the trash while it is still referenced by a product or by anything else".
 * Enforcing it in each service would give thirteen subtly different answers, and the check
 * would drift the moment a new link table appears. Every reference is declared once here, as
 * a count plus the message that names what still points at the row, so the refusal the admin
 * reads is the same sentence whichever endpoint refused.
 *
 * <p>The counts are native SQL on purpose: the linking tables ({@code product_brewing_methods},
 * {@code product_tasting_notes}, {@code order_items}, …) have no JPA entities, and the owning
 * entities carry {@code @SQLRestriction}, which would hide trashed products from a derived
 * count. A trashed product must still block its lookups — restoring it would otherwise leave a
 * product pointing at a row that no longer exists.
 *
 * <p><b>Rule 2 — a permanent delete only touches the trash.</b> {@link #refuseUnlessTrashed}
 * turns the "row is live" case into a 409 instead of letting a purge act on something the admin
 * has not decided to give up yet. Unknown ids keep their 404: the row is not here at all.
 *
 * <p>Databases that still refuse the delete anyway (a reference this class does not know about)
 * surface as {@link org.springframework.dao.DataIntegrityViolationException}, which the global
 * handler already answers with 409 — never a 500 — as long as the service flushes inside its
 * transaction. Permanent-delete methods do.
 */
@Component
public class TrashRules {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * The soft-deletable tables, spelled once. A trash-state probe never builds SQL from a
     * caller-supplied string.
     */
    public enum Table {
        BREWING_METHODS("brewing_methods"),
        CATEGORIES("categories"),
        COFFEE_VARIETIES("coffee_varieties"),
        FARMS("farms"),
        JOURNAL_CATEGORIES("journal_categories"),
        JOURNAL_POSTS("journal_posts"),
        ORDERS("orders"),
        ORIGINS("origins"),
        PAIRINGS("pairings"),
        PROCESSING_METHODS("processing_methods"),
        PRODUCTS("products"),
        PRODUCT_TAGS("product_tags"),
        PRODUCT_VARIANTS("product_variants"),
        PROMO_CODES("promo_codes"),
        REVIEWS("reviews"),
        ROAST_LEVELS("roast_levels"),
        SHIPPING_ZONES("shipping_zones"),
        TASTING_NOTES("tasting_notes"),
        USERS("users");

        private final String table;

        Table(String table) {
            this.table = table;
        }
    }

    /**
     * One way a row can still be in use: how to count the rows that point at it, and the
     * message key that names them.
     */
    public enum Reference {
        /** Products filed under the category. */
        PRODUCT_CATEGORY("SELECT COUNT(*) FROM products WHERE category_id = ?1",
                "error.delete.in.use.products"),
        /** Products roasted from the origin. */
        PRODUCT_ORIGIN("SELECT COUNT(*) FROM products WHERE origin_id = ?1",
                "error.delete.in.use.products"),
        /** Farms registered in the origin — the second hop that makes an origin undeletable. */
        FARM_ORIGIN("SELECT COUNT(*) FROM farms WHERE origin_id = ?1",
                "error.delete.in.use.farms"),
        /** Products grown on the farm. */
        PRODUCT_FARM("SELECT COUNT(*) FROM products WHERE farm_id = ?1",
                "error.delete.in.use.products"),
        /** Products whose coffee details name the roast level. */
        COFFEE_DETAIL_ROAST_LEVEL("SELECT COUNT(*) FROM coffee_details WHERE roast_level_id = ?1",
                "error.delete.in.use.coffeeDetails"),
        /** Products whose coffee details name the processing method. */
        COFFEE_DETAIL_PROCESSING_METHOD(
                "SELECT COUNT(*) FROM coffee_details WHERE processing_method_id = ?1",
                "error.delete.in.use.coffeeDetails"),
        /** Products whose coffee details name the variety. */
        COFFEE_DETAIL_VARIETY("SELECT COUNT(*) FROM coffee_details WHERE coffee_variety_id = ?1",
                "error.delete.in.use.coffeeDetails"),
        /** Products that list the brewing method in their brew guide. */
        PRODUCT_BREWING_METHOD("SELECT COUNT(*) FROM product_brewing_methods WHERE brewing_method_id = ?1",
                "error.delete.in.use.products"),
        /** Products paired with the pairing. */
        PRODUCT_PAIRING("SELECT COUNT(*) FROM product_pairings WHERE pairing_id = ?1",
                "error.delete.in.use.products"),
        /** Products carrying the tasting note. */
        PRODUCT_TASTING_NOTE("SELECT COUNT(*) FROM product_tasting_notes WHERE tasting_note_id = ?1",
                "error.delete.in.use.products"),
        /** Products labelled with the tag. */
        PRODUCT_TAG_LINK("SELECT COUNT(*) FROM product_tag_links WHERE tag_id = ?1",
                "error.delete.in.use.products"),
        /** Journal posts filed under the category — trashed posts count too. */
        JOURNAL_POST_CATEGORY("SELECT COUNT(*) FROM journal_posts WHERE journal_category_id = ?1",
                "error.delete.in.use.posts"),
        /** Orders that recorded the promo code (the totals stay, the reference would be nulled). */
        ORDER_PROMO_CODE("SELECT COUNT(*) FROM orders WHERE promo_code_id = ?1",
                "error.delete.in.use.orders"),
        /** Order lines that recorded the promo code. */
        ORDER_ITEM_PROMO_CODE("SELECT COUNT(*) FROM order_items WHERE promo_code_id = ?1",
                "error.delete.in.use.orders"),
        /** Order lines naming one of the product's variants — the sales history. */
        PRODUCT_ORDER_LINES("SELECT COUNT(*) FROM order_items oi"
                + " JOIN product_variants pv ON pv.id = oi.product_variant_id WHERE pv.product_id = ?1",
                "error.delete.in.use.orderLines"),
        /** Carts still holding one of the product's variants. */
        PRODUCT_CART_ITEMS("SELECT COUNT(*) FROM cart_items ci"
                + " JOIN product_variants pv ON pv.id = ci.product_variant_id WHERE pv.product_id = ?1",
                "error.delete.in.use.cartItems"),
        /** Order lines naming the variant. */
        VARIANT_ORDER_LINES("SELECT COUNT(*) FROM order_items WHERE product_variant_id = ?1",
                "error.delete.in.use.orderLines"),
        /** Carts still holding the variant. */
        VARIANT_CART_ITEMS("SELECT COUNT(*) FROM cart_items WHERE product_variant_id = ?1",
                "error.delete.in.use.cartItems"),
        /** Orders the user placed. */
        USER_ORDER("SELECT COUNT(*) FROM orders WHERE user_id = ?1", "error.delete.in.use.orders"),
        /** Journal posts the user wrote. */
        USER_JOURNAL_POST("SELECT COUNT(*) FROM journal_posts WHERE author_id = ?1",
                "error.delete.in.use.posts");

        private final String sql;
        private final String messageKey;

        Reference(String sql, String messageKey) {
            this.sql = sql;
            this.messageKey = messageKey;
        }
    }

    /**
     * Refuse to trash a row that something still points at.
     *
     * <p>Answers 409 with the referencing thing named and counted, because "still used
     * elsewhere" leaves the admin guessing which of twenty screens to open.
     *
     * @throws EntityInUseException when at least one row references {@code id}
     */
    public void refuseIfReferenced(Reference reference, Long id) {
        requireId(id);
        long count = count(reference, id);
        if (count > 0) {
            throw new EntityInUseException(reference.messageKey, count);
        }
    }

    /**
     * Refuse a permanent delete of a row that is still live.
     *
     * <p>Silent on a trashed row and on an unknown id: the caller's own not-found path answers
     * the unknown id, and keeping that 404 means "no such row" stays distinguishable from
     * "this row is not in the trash yet".
     */
    public void refuseUnlessTrashed(Table table, Long id) {
        Optional<Boolean> trashed = isTrashed(table, id);
        if (Boolean.FALSE.equals(trashed.orElse(null))) {
            throw new NotInTrashException();
        }
    }

    /** Whether the row exists and carries {@code deleted_at}; empty when the id is unknown. */
    public Optional<Boolean> isTrashed(Table table, Long id) {
        if (id == null) {
            return Optional.empty();
        }
        try {
            Object deletedAt = entityManager
                    .createNativeQuery("SELECT deleted_at FROM " + table.table + " WHERE id = ?1")
                    .setParameter(1, id)
                    .getSingleResult();
            return Optional.of(deletedAt != null);
        } catch (NoResultException missing) {
            return Optional.empty();
        }
    }

    private long count(Reference reference, Long id) {
        Object value = entityManager.createNativeQuery(reference.sql)
                .setParameter(1, id)
                .getSingleResult();
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static void requireId(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("A trash rule needs the row id");
        }
    }
}
