package com.nocteon.nocteon_api.trash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.brewingMethod.service.BrewingMethodService;
import com.nocteon.nocteon_api.category.service.CategoryService;
import com.nocteon.nocteon_api.coffeeVariety.service.CoffeeVarietyService;
import com.nocteon.nocteon_api.common.exception.invalid.EntityInUseException;
import com.nocteon.nocteon_api.common.exception.invalid.NotInTrashException;
import com.nocteon.nocteon_api.farm.service.FarmService;
import com.nocteon.nocteon_api.journal.service.JournalService;
import com.nocteon.nocteon_api.origin.service.OriginService;
import com.nocteon.nocteon_api.pairing.service.PairingService;
import com.nocteon.nocteon_api.processingMethod.service.ProcessingMethodService;
import com.nocteon.nocteon_api.product.service.ProductService;
import com.nocteon.nocteon_api.product.service.ProductTagService;
import com.nocteon.nocteon_api.promoCode.service.PromoCodeService;
import com.nocteon.nocteon_api.roastLevel.service.RoastLevelService;
import com.nocteon.nocteon_api.shippingZone.service.ShippingZoneService;
import com.nocteon.nocteon_api.tastingNote.service.TastingNoteService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * The two trash rules, end to end against the real schema.
 *
 * <p>Every test builds its own rows under a {@code zz-trash-} prefix and removes them in a
 * {@code finally} block: the development database is shared, so a test that counted rows, or
 * assumed an id, would pass or fail depending on what ran before it. Nothing here reads a global
 * count and nothing depends on the seed.
 *
 * <p>What is under test is the decision, not the HTTP plumbing: a refusal is an
 * {@link EntityInUseException} carrying the referencing message key and a 409 status, so the
 * dashboard shows the backend's sentence verbatim; a purge on a live row is
 * {@link NotInTrashException} (also 409); a purge the database refuses surfaces as
 * {@link DataIntegrityViolationException}, which the global handler turns into the same 409
 * instead of a 500.
 */
@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TrashRulesIntegrationTest {

    @Autowired
    private CategoryService categoryService;
    @Autowired
    private OriginService originService;
    @Autowired
    private FarmService farmService;
    @Autowired
    private RoastLevelService roastLevelService;
    @Autowired
    private ProcessingMethodService processingMethodService;
    @Autowired
    private CoffeeVarietyService coffeeVarietyService;
    @Autowired
    private TastingNoteService tastingNoteService;
    @Autowired
    private PairingService pairingService;
    @Autowired
    private BrewingMethodService brewingMethodService;
    @Autowired
    private JournalService journalService;
    @Autowired
    private ProductTagService productTagService;
    @Autowired
    private ProductService productService;
    @Autowired
    private PromoCodeService promoCodeService;
    @Autowired
    private ShippingZoneService shippingZoneService;
    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    // ─── fixtures ───────────────────────────────────────────────────────────────

    private static String slug() {
        return "zz-trash-" + System.nanoTime();
    }

    private long id(String table, String slugValue) {
        return jdbc.queryForObject("select id from " + table + " where slug = ?", Long.class, slugValue);
    }

    private boolean isTrashed(String table, long id) {
        Boolean deleted = jdbc.queryForObject(
                "select deleted_at is not null from " + table + " where id = ?", Boolean.class, id);
        return Boolean.TRUE.equals(deleted);
    }

    /** Translations are what every catalogue read joins on, so each fixture writes en + ar. */
    private void translations(String table, String fk, long id, String name) {
        jdbc.update("insert into " + table + " (" + fk + ", language, name) values (?, 'en', ?)", id, name);
        jdbc.update("insert into " + table + " (" + fk + ", language, name) values (?, 'ar', ?)", id, name);
    }

    private long category(String slug, boolean trashed) {
        if (trashed) {
            jdbc.update("insert into categories (slug, is_active, deleted_at) values (?, true, now())", slug);
        } else {
            jdbc.update("insert into categories (slug, is_active) values (?, true)", slug);
        }
        long id = id("categories", slug);
        translations("category_translations", "category_id", id, slug);
        return id;
    }

    private long origin(String slug) {
        jdbc.update("insert into origins (slug, code) values (?, 'ZZ')", slug);
        long id = id("origins", slug);
        translations("origin_translations", "origin_id", id, slug);
        return id;
    }

    private long product(long categoryId, String slug) {
        jdbc.update("insert into products (slug, category_id, product_type) values (?, ?, 'COFFEE')",
                slug, categoryId);
        long id = id("products", slug);
        translations("product_translations", "product_id", id, slug);
        return id;
    }

    private long variant(long productId, String sku) {
        jdbc.update("insert into product_variants (product_id, sku, price) values (?, ?, 100)", productId, sku);
        return jdbc.queryForObject("select id from product_variants where sku = ?", Long.class, sku);
    }

    private long lookup(String table, String translationTable, String fk, String slug, String extraColumn,
            Object extraValue) {
        if (extraColumn == null) {
            jdbc.update("insert into " + table + " (slug) values (?)", slug);
        } else {
            jdbc.update("insert into " + table + " (slug, " + extraColumn + ") values (?, ?)", slug, extraValue);
        }
        long id = id(table, slug);
        translations(translationTable, fk, id, slug);
        return id;
    }

    private long roastLevel(String slug) {
        return lookup("roast_levels", "roast_level_translations", "roast_level_id", slug, "color", "#7a4b2a");
    }

    private long plainLookup(String table, String translationTable, String fk, String slug) {
        return lookup(table, translationTable, fk, slug, null, null);
    }

    private void purge(String... statements) {
        for (String statement : statements) {
            jdbc.update(statement);
        }
    }

    // ─── Rule 1: a referenced row cannot be trashed ─────────────────────────────

    @Test
    void aCategoryAProductUsesIsRefusedAndStaysLive() {
        String slug = slug();
        long categoryId = category(slug, false);
        product(categoryId, slug + "-p");
        try {
            assertThatThrownBy(() -> categoryService.delete(slug))
                    .isInstanceOf(EntityInUseException.class)
                    .satisfies(e -> {
                        EntityInUseException inUse = (EntityInUseException) e;
                        assertThat(inUse.getMessageKey()).isEqualTo("error.delete.in.use.products");
                        assertThat(inUse.getCount()).isEqualTo(1);
                        assertThat(inUse.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    });

            // The refusal must not have touched deleted_at: a trashed category disappears from
            // every listing and takes its products' navigation with it.
            assertThat(isTrashed("categories", categoryId)).isFalse();
        } finally {
            purge("delete from product_translations where product_id in (select id from products where slug = '" + slug + "-p')",
                    "delete from products where slug = '" + slug + "-p'",
                    "delete from category_translations where category_id = " + categoryId,
                    "delete from categories where id = " + categoryId);
        }
    }

    @Test
    void anOriginAFarmBelongsToIsRefused() {
        String slug = slug();
        long originId = origin(slug);
        jdbc.update("insert into farms (origin_id, slug) values (?, ?)", originId, slug + "-f");
        try {
            assertThatThrownBy(() -> originService.delete(slug))
                    .isInstanceOf(EntityInUseException.class)
                    .satisfies(e -> assertThat(((EntityInUseException) e).getMessageKey())
                            .isEqualTo("error.delete.in.use.farms"));
            assertThat(isTrashed("origins", originId)).isFalse();
        } finally {
            purge("delete from farms where slug = '" + slug + "-f'",
                    "delete from origin_translations where origin_id = " + originId,
                    "delete from origins where id = " + originId);
        }
    }

    @Test
    void aFarmAProductGrowsOnIsRefused() {
        String slug = slug();
        long originId = origin(slug + "-o");
        long categoryId = category(slug + "-c", false);
        jdbc.update("insert into farms (origin_id, slug) values (?, ?)", originId, slug);
        long farmId = id("farms", slug);
        translations("farm_translations", "farm_id", farmId, slug);
        jdbc.update("insert into products (slug, category_id, farm_id, product_type) values (?, ?, ?, 'COFFEE')",
                slug + "-p", categoryId, farmId);
        try {
            assertThatThrownBy(() -> farmService.delete(slug))
                    .isInstanceOf(EntityInUseException.class)
                    .satisfies(e -> assertThat(((EntityInUseException) e).getMessageKey())
                            .isEqualTo("error.delete.in.use.products"));
            assertThat(isTrashed("farms", farmId)).isFalse();
        } finally {
            purge("delete from product_translations where product_id in (select id from products where slug = '" + slug + "-p')",
                    "delete from products where slug = '" + slug + "-p'",
                    "delete from farm_translations where farm_id = " + farmId,
                    "delete from farms where id = " + farmId,
                    "delete from category_translations where category_id = " + categoryId,
                    "delete from categories where id = " + categoryId,
                    "delete from origin_translations where origin_id = " + originId,
                    "delete from origins where id = " + originId);
        }
    }

    @Test
    void coffeeDetailsMakeARoastLevelAProcessingMethodAndAVarietyRefused() {
        String slug = slug();
        long categoryId = category(slug + "-c", false);
        long productId = product(categoryId, slug + "-p");
        long roastId = roastLevel(slug + "-r");
        long methodId = plainLookup("processing_methods", "processing_method_translations",
                "processing_method_id", slug + "-m");
        long varietyId = plainLookup("coffee_varieties", "coffee_variety_translations",
                "coffee_variety_id", slug + "-v");
        jdbc.update("insert into coffee_details (product_id, roast_level_id, processing_method_id, coffee_variety_id)"
                + " values (?, ?, ?, ?)", productId, roastId, methodId, varietyId);
        try {
            assertThatThrownBy(() -> roastLevelService.delete(slug + "-r"))
                    .isInstanceOf(EntityInUseException.class)
                    .satisfies(e -> assertThat(((EntityInUseException) e).getMessageKey())
                            .isEqualTo("error.delete.in.use.coffeeDetails"));
            assertThatThrownBy(() -> processingMethodService.delete(slug + "-m"))
                    .isInstanceOf(EntityInUseException.class)
                    .satisfies(e -> assertThat(((EntityInUseException) e).getMessageKey())
                            .isEqualTo("error.delete.in.use.coffeeDetails"));
            assertThatThrownBy(() -> coffeeVarietyService.delete(slug + "-v"))
                    .isInstanceOf(EntityInUseException.class)
                    .satisfies(e -> assertThat(((EntityInUseException) e).getMessageKey())
                            .isEqualTo("error.delete.in.use.coffeeDetails"));

            assertThat(isTrashed("roast_levels", roastId)).isFalse();
            assertThat(isTrashed("processing_methods", methodId)).isFalse();
            assertThat(isTrashed("coffee_varieties", varietyId)).isFalse();
        } finally {
            purge("delete from coffee_details where product_id = " + productId,
                    "delete from product_translations where product_id = " + productId,
                    "delete from products where id = " + productId,
                    "delete from category_translations where category_id = " + categoryId,
                    "delete from categories where id = " + categoryId,
                    "delete from roast_level_translations where roast_level_id = " + roastId,
                    "delete from roast_levels where id = " + roastId,
                    "delete from processing_method_translations where processing_method_id = " + methodId,
                    "delete from processing_methods where id = " + methodId,
                    "delete from coffee_variety_translations where coffee_variety_id = " + varietyId,
                    "delete from coffee_varieties where id = " + varietyId);
        }
    }

    @Test
    void brewGuidePairingAndTastingNoteLinksAreRefused() {
        String slug = slug();
        long categoryId = category(slug + "-c", false);
        long productId = product(categoryId, slug + "-p");
        long brewingId = plainLookup("brewing_methods", "brewing_method_translations",
                "brewing_method_id", slug + "-b");
        long pairingId = plainLookup("pairings", "pairing_translations", "pairing_id", slug + "-x");
        long noteId = plainLookup("tasting_notes", "tasting_note_translations", "tasting_note_id", slug + "-n");
        jdbc.update("insert into product_brewing_methods (product_id, brewing_method_id) values (?, ?)",
                productId, brewingId);
        jdbc.update("insert into product_pairings (product_id, pairing_id) values (?, ?)", productId, pairingId);
        jdbc.update("insert into product_tasting_notes (product_id, tasting_note_id) values (?, ?)",
                productId, noteId);
        try {
            assertThatThrownBy(() -> brewingMethodService.delete(slug + "-b"))
                    .isInstanceOf(EntityInUseException.class)
                    .satisfies(e -> assertThat(((EntityInUseException) e).getMessageKey())
                            .isEqualTo("error.delete.in.use.products"));
            assertThatThrownBy(() -> pairingService.delete(slug + "-x"))
                    .isInstanceOf(EntityInUseException.class)
                    .satisfies(e -> assertThat(((EntityInUseException) e).getMessageKey())
                            .isEqualTo("error.delete.in.use.products"));
            assertThatThrownBy(() -> tastingNoteService.delete(slug + "-n"))
                    .isInstanceOf(EntityInUseException.class)
                    .satisfies(e -> assertThat(((EntityInUseException) e).getMessageKey())
                            .isEqualTo("error.delete.in.use.products"));
        } finally {
            purge("delete from product_brewing_methods where product_id = " + productId,
                    "delete from product_pairings where product_id = " + productId,
                    "delete from product_tasting_notes where product_id = " + productId,
                    "delete from product_translations where product_id = " + productId,
                    "delete from products where id = " + productId,
                    "delete from category_translations where category_id = " + categoryId,
                    "delete from categories where id = " + categoryId,
                    "delete from brewing_method_translations where brewing_method_id = " + brewingId,
                    "delete from brewing_methods where id = " + brewingId,
                    "delete from pairing_translations where pairing_id = " + pairingId,
                    "delete from pairings where id = " + pairingId,
                    "delete from tasting_note_translations where tasting_note_id = " + noteId,
                    "delete from tasting_notes where id = " + noteId);
        }
    }

    @Test
    void aJournalCategoryStillHoldingAPostIsRefused() {
        String slug = slug();
        jdbc.update("insert into journal_categories (slug) values (?)", slug);
        long categoryId = id("journal_categories", slug);
        long authorId = jdbc.queryForObject("select min(id) from users", Long.class);
        jdbc.update("insert into journal_posts (journal_category_id, author_id, slug) values (?, ?, ?)",
                categoryId, authorId, slug + "-post");
        try {
            assertThatThrownBy(() -> journalService.deleteCategory(categoryId))
                    .isInstanceOf(EntityInUseException.class)
                    .satisfies(e -> {
                        EntityInUseException inUse = (EntityInUseException) e;
                        assertThat(inUse.getMessageKey()).isEqualTo("error.delete.in.use.posts");
                        assertThat(inUse.getCount()).isEqualTo(1);
                    });
            assertThat(isTrashed("journal_categories", categoryId)).isFalse();
        } finally {
            purge("delete from journal_posts where slug = '" + slug + "-post'",
                    "delete from journal_categories where id = " + categoryId);
        }
    }

    @Test
    void aTagProductsCarryIsRefused() {
        String slug = slug();
        jdbc.update("insert into product_tags (slug) values (?)", slug);
        long tagId = id("product_tags", slug);
        long categoryId = category(slug + "-c", false);
        long productId = product(categoryId, slug + "-p");
        jdbc.update("insert into product_tag_links (product_id, tag_id) values (?, ?)", productId, tagId);
        try {
            assertThatThrownBy(() -> productTagService.deleteTag(tagId))
                    .isInstanceOf(EntityInUseException.class)
                    .satisfies(e -> assertThat(((EntityInUseException) e).getMessageKey())
                            .isEqualTo("error.delete.in.use.products"));
            assertThat(isTrashed("product_tags", tagId)).isFalse();
        } finally {
            purge("delete from product_tag_links where tag_id = " + tagId,
                    "delete from product_translations where product_id = " + productId,
                    "delete from products where id = " + productId,
                    "delete from category_translations where category_id = " + categoryId,
                    "delete from categories where id = " + categoryId,
                    "delete from product_tags where id = " + tagId);
        }
    }

    @Test
    void aProductOnAnOrderAndAPromoCodeAnOrderNamesAreRefused() {
        String slug = slug();
        long categoryId = category(slug + "-c", false);
        long productId = product(categoryId, slug + "-p");
        long variantId = variant(productId, slug + "-sku");
        long promoId = promoCode(slug + "-promo");

        long userId = jdbc.queryForObject("select min(user_id) from addresses", Long.class);
        long addressId = jdbc.queryForObject(
                "select min(id) from addresses where user_id = ?", Long.class, userId);
        jdbc.update("insert into orders (user_id, address_id, order_number, total_amount, promo_code_id)"
                + " values (?, ?, ?, 100, ?)", userId, addressId, slug + "-order", promoId);
        long orderId = jdbc.queryForObject("select id from orders where order_number = ?", Long.class, slug + "-order");
        jdbc.update("insert into order_items (order_id, product_variant_id, quantity, unit_price, total_price)"
                + " values (?, ?, 1, 100, 100)", orderId, variantId);
        try {
            assertThatThrownBy(() -> productService.delete(slug + "-p"))
                    .isInstanceOf(EntityInUseException.class)
                    .satisfies(e -> assertThat(((EntityInUseException) e).getMessageKey())
                            .isEqualTo("error.delete.in.use.orderLines"));

            assertThatThrownBy(() -> promoCodeService.delete(promoId))
                    .isInstanceOf(EntityInUseException.class)
                    .satisfies(e -> assertThat(((EntityInUseException) e).getMessageKey())
                            .isEqualTo("error.delete.in.use.orders"));

            assertThat(isTrashed("products", productId)).isFalse();
            assertThat(isTrashed("promo_codes", promoId)).isFalse();
        } finally {
            purge("delete from order_items where order_id = " + orderId,
                    "delete from orders where id = " + orderId,
                    "delete from product_variants where id = " + variantId,
                    "delete from product_translations where product_id = " + productId,
                    "delete from products where id = " + productId,
                    "delete from category_translations where category_id = " + categoryId,
                    "delete from categories where id = " + categoryId,
                    "delete from promo_codes where id = " + promoId);
        }
    }

    // ─── what is deliberately allowed ───────────────────────────────────────────

    @Test
    void anUnreferencedShippingZoneAndPromoCodeCanBeTrashed() {
        String slug = slug();
        jdbc.update("insert into shipping_zones (name, shipping_cost) values (?, 55)", slug);
        long zoneId = jdbc.queryForObject("select id from shipping_zones where name = ?", Long.class, slug);
        long promoId = promoCode(slug + "-promo");
        try {
            // Nothing can point at a shipping zone: orders snapshot the address and the cost, so
            // deleting one is history-neutral. A promo code no order names is the same.
            shippingZoneService.delete(zoneId);
            promoCodeService.delete(promoId);

            assertThat(isTrashed("shipping_zones", zoneId)).isTrue();
            assertThat(isTrashed("promo_codes", promoId)).isTrue();
        } finally {
            purge("delete from shipping_zones where id = " + zoneId,
                    "delete from promo_codes where id = " + promoId);
        }
    }

    // ─── Rule 2: permanent delete only touches the trash ────────────────────────

    @Test
    void permanentDeleteOfALiveRowIsAConflictAndLeavesItAlone() {
        String slug = slug();
        long categoryId = category(slug, false);
        try {
            assertThatThrownBy(() -> categoryService.permanentDelete(categoryId))
                    .isInstanceOf(NotInTrashException.class)
                    .satisfies(e -> {
                        NotInTrashException notInTrash = (NotInTrashException) e;
                        assertThat(notInTrash.getMessageKey()).isEqualTo("error.delete.notInTrash");
                        assertThat(notInTrash.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    });
            assertThat(isTrashed("categories", categoryId)).isFalse();
        } finally {
            purge("delete from category_translations where category_id = " + categoryId,
                    "delete from categories where id = " + categoryId);
        }
    }

    @Test
    void permanentDeleteOfATrashedUnreferencedRowRemovesItAndAuditsOnce() {
        String slug = slug();
        long categoryId = category(slug, true);
        jdbc.update("delete from audit_logs where entity_type = 'Category' and entity_id = ?", categoryId);
        try {
            categoryService.permanentDelete(categoryId);

            Integer rows = jdbc.queryForObject(
                    "select count(*) from categories where id = ?", Integer.class, categoryId);
            assertThat(rows).isZero();
            assertThat(jdbc.queryForObject(
                    "select count(*) from category_translations where category_id = ?", Integer.class, categoryId))
                    .isZero();

            List<String> actions = jdbc.queryForList(
                    "select action from audit_logs where entity_type = 'Category' and entity_id = ?",
                    String.class, categoryId);
            assertThat(actions).containsExactly("ENTITY_PERMANENT_DELETE");
        } finally {
            purge("delete from audit_logs where entity_type = 'Category' and entity_id = " + categoryId,
                    "delete from category_translations where category_id = " + categoryId,
                    "delete from categories where id = " + categoryId);
        }
    }

    @Test
    void permanentDeleteTheDatabaseRefusesIsAConflictNotAServerError() {
        // A product that reached the trash before anyone ordered it, with order lines added
        // afterwards: the trash rule could not see them, so the database has the last word.
        String slug = slug();
        long categoryId = category(slug + "-c", false);
        long productId = product(categoryId, slug + "-p");
        long variantId = variant(productId, slug + "-sku");
        jdbc.update("update products set deleted_at = now() where id = ?", productId);

        long userId = jdbc.queryForObject("select min(user_id) from addresses", Long.class);
        long addressId = jdbc.queryForObject(
                "select min(id) from addresses where user_id = ?", Long.class, userId);
        jdbc.update("insert into orders (user_id, address_id, order_number, total_amount) values (?, ?, ?, 100)",
                userId, addressId, slug + "-order");
        long orderId = jdbc.queryForObject("select id from orders where order_number = ?", Long.class, slug + "-order");
        jdbc.update("insert into order_items (order_id, product_variant_id, quantity, unit_price, total_price)"
                + " values (?, ?, 1, 100, 100)", orderId, variantId);
        try {
            assertThatThrownBy(() -> productService.permanentDelete(productId))
                    .isInstanceOf(DataIntegrityViolationException.class);

            // Rolled back: the product and its variant are still there, so the admin can retry.
            assertThat(isTrashed("products", productId)).isTrue();
            assertThat(jdbc.queryForObject(
                    "select count(*) from product_variants where id = ?", Integer.class, variantId)).isEqualTo(1);
        } finally {
            purge("delete from order_items where order_id = " + orderId,
                    "delete from orders where id = " + orderId,
                    "delete from product_variants where id = " + variantId,
                    "delete from product_translations where product_id = " + productId,
                    "delete from products where id = " + productId,
                    "delete from category_translations where category_id = " + categoryId,
                    "delete from categories where id = " + categoryId);
        }
    }

    private long promoCode(String code) {
        jdbc.update("insert into promo_codes (code, discount_type, discount_value, scope_type, valid_from, valid_until)"
                + " values (?, 'PERCENTAGE', 10, 'GLOBAL', now() - interval '1 day', now() + interval '30 days')", code);
        return jdbc.queryForObject("select id from promo_codes where code = ?", Long.class, code);
    }
}
