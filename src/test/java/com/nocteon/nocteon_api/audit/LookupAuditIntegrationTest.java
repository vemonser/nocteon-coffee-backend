package com.nocteon.nocteon_api.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.enums.Provider;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.brewingMethod.dto.request.BrewingMethodRequest;
import com.nocteon.nocteon_api.brewingMethod.dto.request.BrewingMethodTranslationRequest;
import com.nocteon.nocteon_api.brewingMethod.service.BrewingMethodService;
import com.nocteon.nocteon_api.category.dto.request.CategoryRequest;
import com.nocteon.nocteon_api.category.dto.request.CreateCategoryTranslationRequest;
import com.nocteon.nocteon_api.category.service.CategoryService;
import com.nocteon.nocteon_api.coffeeVariety.dto.request.CoffeeVarietyRequest;
import com.nocteon.nocteon_api.coffeeVariety.dto.request.CoffeeVarietyTranslationRequest;
import com.nocteon.nocteon_api.coffeeVariety.service.CoffeeVarietyService;
import com.nocteon.nocteon_api.common.util.SlugUtils;
import com.nocteon.nocteon_api.farm.dto.request.FarmRequest;
import com.nocteon.nocteon_api.farm.dto.request.FarmTranslationRequest;
import com.nocteon.nocteon_api.farm.service.FarmService;
import com.nocteon.nocteon_api.origin.dto.request.OriginRequest;
import com.nocteon.nocteon_api.origin.dto.request.OriginTranslationRequest;
import com.nocteon.nocteon_api.origin.service.OriginService;
import com.nocteon.nocteon_api.pairing.dto.request.PairingRequest;
import com.nocteon.nocteon_api.pairing.dto.request.PairingTranslationRequest;
import com.nocteon.nocteon_api.pairing.service.PairingService;
import com.nocteon.nocteon_api.processingMethod.dto.request.ProcessingMethodRequest;
import com.nocteon.nocteon_api.processingMethod.dto.request.ProcessingMethodTranslationRequest;
import com.nocteon.nocteon_api.processingMethod.service.ProcessingMethodService;
import com.nocteon.nocteon_api.roastLevel.dto.request.RoastLevelRequest;
import com.nocteon.nocteon_api.roastLevel.dto.request.RoastLevelTranslationRequest;
import com.nocteon.nocteon_api.roastLevel.service.RoastLevelService;
import com.nocteon.nocteon_api.support.TestJwtKeys;
import com.nocteon.nocteon_api.tastingNote.dto.request.TastingNoteRequest;
import com.nocteon.nocteon_api.tastingNote.dto.request.TastingNoteTranslationRequest;
import com.nocteon.nocteon_api.tastingNote.service.TastingNoteService;

/**
 * The COFFEE-DATA half of the audit log, for all nine catalogues the dashboard
 * manages: categories, origins, farms, roast levels, processing methods, coffee
 * varieties, tasting notes, pairings and brewing methods.
 *
 * <p>They do not share a base class, so the coverage is wired once in
 * {@code DomainAuditRecorder} with the entity type in the payload. This test is the
 * proof that each service actually reaches it: one parametrised run per catalogue,
 * create then rename then delete, asserting the action, the entity, the actor and
 * the field that changed.
 *
 * <p>Every fixture name carries the same random suffix and cleanup is scoped to it.
 * This suite runs against a shared dev database, so nothing here counts rows it did
 * not create. The farm case creates its own origin first — a farm cannot exist
 * without one.
 */
@SpringBootTest(classes = NocteonApiApplication.class)
class LookupAuditIntegrationTest {

    /** One row per managed catalogue: what to call it, and where it lives. */
    enum Lookup {
        CATEGORY("Category", "categories", "category_translations", "category_id"),
        ORIGIN("Origin", "origins", "origin_translations", "origin_id"),
        FARM("Farm", "farms", "farm_translations", "farm_id"),
        ROAST_LEVEL("RoastLevel", "roast_levels", "roast_level_translations", "roast_level_id"),
        PROCESSING_METHOD("ProcessingMethod", "processing_methods",
                "processing_method_translations", "processing_method_id"),
        COFFEE_VARIETY("CoffeeVariety", "coffee_varieties", "coffee_variety_translations", "coffee_variety_id"),
        TASTING_NOTE("TastingNote", "tasting_notes", "tasting_note_translations", "tasting_note_id"),
        PAIRING("Pairing", "pairings", "pairing_translations", "pairing_id"),
        BREWING_METHOD("BrewingMethod", "brewing_methods", "brewing_method_translations", "brewing_method_id");

        final String entityType;
        final String table;
        final String translationTable;
        final String foreignKey;

        Lookup(String entityType, String table, String translationTable, String foreignKey) {
            this.entityType = entityType;
            this.table = table;
            this.translationTable = translationTable;
            this.foreignKey = foreignKey;
        }

        /** The human label used in fixture names: {@code roast level}. */
        String label() {
            return name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryService categoryService;
    @Autowired private OriginService originService;
    @Autowired private FarmService farmService;
    @Autowired private RoastLevelService roastLevelService;
    @Autowired private ProcessingMethodService processingMethodService;
    @Autowired private CoffeeVarietyService coffeeVarietyService;
    @Autowired private TastingNoteService tastingNoteService;
    @Autowired private PairingService pairingService;
    @Autowired private BrewingMethodService brewingMethodService;

    @org.springframework.test.context.DynamicPropertySource
    static void jwtKeys(org.springframework.test.context.DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    private String suffix;
    private User admin;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        deleteFixtures();
        admin = userRepository.save(User.builder()
                .username("auditcov-lookup-admin-" + suffix)
                .email("auditcov-lookup-admin-" + suffix + "@example.test")
                .password("x")
                .provider(Provider.LOCAL)
                .role("ADMIN")
                .enabled(true)
                .build());
        actingAsAdmin();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        deleteFixtures();
    }

    static Stream<Arguments> lookups() {
        return Stream.of(Lookup.values()).map(Arguments::of);
    }

    @ParameterizedTest(name = "{0} create, update and delete each leave a row")
    @MethodSource("lookups")
    void createUpdateAndDeleteAreAudited(Lookup lookup) {
        String name = "Auditcov lookup " + lookup.label() + " " + suffix;
        String renamed = "Auditcov lookup " + lookup.label() + " " + suffix + " renamed";
        String slug = SlugUtils.toSlug(name);

        create(lookup, name);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM " + lookup.table + " WHERE slug = ?", Long.class, slug);
        assertThat(id).as("%s row for %s", lookup.entityType, slug).isNotNull();

        Map<String, Object> created = latestRow(AuditAction.LOOKUP_CREATE, lookup, id);
        assertThat(created.get("user_id")).isEqualTo(admin.getId());
        assertThat(created.get("user_email")).isEqualTo(admin.getEmail());
        assertThat(created.get("entity_type")).isEqualTo(lookup.entityType);
        assertThat(created.get("entity_slug")).isEqualTo(slug);
        assertThat(newValues(created)).containsEntry("name", name);
        assertThat(oldValues(created)).isEmpty();

        update(lookup, name, renamed);
        Map<String, Object> updated = latestRow(AuditAction.LOOKUP_UPDATE, lookup, id);
        assertThat(updated.get("user_id")).isEqualTo(admin.getId());
        assertThat(updated.get("user_email")).isEqualTo(admin.getEmail());
        assertThat(oldValues(updated)).containsEntry("name", name);
        assertThat(newValues(updated)).containsEntry("name", renamed);

        delete(lookup, name);
        Map<String, Object> deleted = latestRow(AuditAction.LOOKUP_DELETE, lookup, id);
        assertThat(deleted.get("user_id")).isEqualTo(admin.getId());
        assertThat(oldValues(deleted)).containsEntry("deletedAt", null);
        assertThat(String.valueOf(newValues(deleted).get("deletedAt"))).isNotBlank();
    }

    // ── one branch per catalogue ─────────────────────────────────────────────

    private void create(Lookup lookup, String name) {
        switch (lookup) {
            case CATEGORY -> categoryService.create(categoryRequest(name), null);
            case ORIGIN -> originService.create(originRequest(name), null);
            case FARM -> {
                originService.create(originRequest(name + " Origin"), null);
                farmService.create(farmRequest(name, SlugUtils.toSlug(name + " Origin")), null);
            }
            case ROAST_LEVEL -> roastLevelService.create(roastLevelRequest(name));
            case PROCESSING_METHOD -> processingMethodService.create(processingMethodRequest(name));
            case COFFEE_VARIETY -> coffeeVarietyService.create(coffeeVarietyRequest(name));
            case TASTING_NOTE -> tastingNoteService.create(tastingNoteRequest(name));
            case PAIRING -> pairingService.create(pairingRequest(name), null);
            case BREWING_METHOD -> brewingMethodService.create(brewingMethodRequest(name));
        }
    }

    private void update(Lookup lookup, String name, String renamed) {
        String slug = SlugUtils.toSlug(name);
        switch (lookup) {
            case CATEGORY -> categoryService.update(slug, categoryRequest(renamed), null);
            case ORIGIN -> originService.update(slug, originRequest(renamed), null);
            case FARM -> farmService.update(slug, farmRequest(renamed, SlugUtils.toSlug(name + " Origin")), null);
            case ROAST_LEVEL -> roastLevelService.update(slug, roastLevelRequest(renamed));
            case PROCESSING_METHOD -> processingMethodService.update(slug, processingMethodRequest(renamed));
            case COFFEE_VARIETY -> coffeeVarietyService.update(slug, coffeeVarietyRequest(renamed));
            case TASTING_NOTE -> tastingNoteService.update(slug, tastingNoteRequest(renamed));
            case PAIRING -> pairingService.update(slug, pairingRequest(renamed), null);
            case BREWING_METHOD -> brewingMethodService.update(slug, brewingMethodRequest(renamed));
        }
    }

    private void delete(Lookup lookup, String name) {
        String slug = SlugUtils.toSlug(name);
        switch (lookup) {
            case CATEGORY -> categoryService.delete(slug);
            case ORIGIN -> originService.delete(slug);
            case FARM -> farmService.delete(slug);
            case ROAST_LEVEL -> roastLevelService.delete(slug);
            case PROCESSING_METHOD -> processingMethodService.delete(slug);
            case COFFEE_VARIETY -> coffeeVarietyService.delete(slug);
            case TASTING_NOTE -> tastingNoteService.delete(slug);
            case PAIRING -> pairingService.delete(slug);
            case BREWING_METHOD -> brewingMethodService.delete(slug);
        }
    }

    // ── request fixtures ─────────────────────────────────────────────────────

    /** Both languages: every lookup rejects a request that is not en + ar. */
    private CategoryRequest categoryRequest(String name) {
        CreateCategoryTranslationRequest english = new CreateCategoryTranslationRequest();
        english.setLanguage("en");
        english.setName(name);
        english.setDescription("audit fixture");

        CreateCategoryTranslationRequest arabic = new CreateCategoryTranslationRequest();
        arabic.setLanguage("ar");
        arabic.setName("اختبار " + suffix);

        CategoryRequest request = new CategoryRequest();
        request.setIsActive(true);
        request.setTranslations(List.of(english, arabic));
        return request;
    }

    private OriginRequest originRequest(String name) {
        OriginTranslationRequest english = new OriginTranslationRequest();
        english.setLanguage("en");
        english.setName(name);
        english.setDescription("audit fixture");

        OriginTranslationRequest arabic = new OriginTranslationRequest();
        arabic.setLanguage("ar");
        arabic.setName("اختبار " + suffix);

        OriginRequest request = new OriginRequest();
        // origins.code is NOT NULL; the value does not change on the rename, so it stays
        // out of the UPDATE row.
        request.setCode("A" + suffix.toUpperCase(java.util.Locale.ROOT));
        request.setTranslations(List.of(english, arabic));
        return request;
    }

    private FarmRequest farmRequest(String name, String originSlug) {
        FarmTranslationRequest english = new FarmTranslationRequest();
        english.setLanguage("en");
        english.setName(name);
        english.setDescription("audit fixture");

        FarmTranslationRequest arabic = new FarmTranslationRequest();
        arabic.setLanguage("ar");
        arabic.setName("اختبار " + suffix);

        FarmRequest request = new FarmRequest();
        request.setOriginSlug(originSlug);
        request.setTranslations(List.of(english, arabic));
        return request;
    }

    private RoastLevelRequest roastLevelRequest(String name) {
        RoastLevelTranslationRequest english = new RoastLevelTranslationRequest();
        english.setLanguage("en");
        english.setName(name);
        english.setDescription("audit fixture");

        RoastLevelTranslationRequest arabic = new RoastLevelTranslationRequest();
        arabic.setLanguage("ar");
        arabic.setName("اختبار " + suffix);

        RoastLevelRequest request = new RoastLevelRequest();
        request.setColor("Medium");
        request.setTranslations(List.of(english, arabic));
        return request;
    }

    private ProcessingMethodRequest processingMethodRequest(String name) {
        ProcessingMethodTranslationRequest english = new ProcessingMethodTranslationRequest();
        english.setLanguage("en");
        english.setName(name);
        english.setDescription("audit fixture");

        ProcessingMethodTranslationRequest arabic = new ProcessingMethodTranslationRequest();
        arabic.setLanguage("ar");
        arabic.setName("اختبار " + suffix);

        ProcessingMethodRequest request = new ProcessingMethodRequest();
        request.setTranslations(List.of(english, arabic));
        return request;
    }

    private CoffeeVarietyRequest coffeeVarietyRequest(String name) {
        CoffeeVarietyTranslationRequest english = new CoffeeVarietyTranslationRequest();
        english.setLanguage("en");
        english.setName(name);
        english.setDescription("audit fixture");

        CoffeeVarietyTranslationRequest arabic = new CoffeeVarietyTranslationRequest();
        arabic.setLanguage("ar");
        arabic.setName("اختبار " + suffix);

        CoffeeVarietyRequest request = new CoffeeVarietyRequest();
        request.setTranslations(List.of(english, arabic));
        return request;
    }

    private TastingNoteRequest tastingNoteRequest(String name) {
        TastingNoteTranslationRequest english = new TastingNoteTranslationRequest();
        english.setLanguage("en");
        english.setName(name);

        TastingNoteTranslationRequest arabic = new TastingNoteTranslationRequest();
        arabic.setLanguage("ar");
        arabic.setName("اختبار " + suffix);

        TastingNoteRequest request = new TastingNoteRequest();
        request.setTranslations(List.of(english, arabic));
        return request;
    }

    private PairingRequest pairingRequest(String name) {
        PairingTranslationRequest english = new PairingTranslationRequest();
        english.setLanguage("en");
        english.setName(name);
        english.setDescription("audit fixture");

        PairingTranslationRequest arabic = new PairingTranslationRequest();
        arabic.setLanguage("ar");
        arabic.setName("اختبار " + suffix);

        PairingRequest request = new PairingRequest();
        request.setTranslations(List.of(english, arabic));
        return request;
    }

    private BrewingMethodRequest brewingMethodRequest(String name) {
        BrewingMethodTranslationRequest english = new BrewingMethodTranslationRequest();
        english.setLanguage("en");
        english.setName(name);
        english.setDescription("audit fixture");

        BrewingMethodTranslationRequest arabic = new BrewingMethodTranslationRequest();
        arabic.setLanguage("ar");
        arabic.setName("اختبار " + suffix);

        BrewingMethodRequest request = new BrewingMethodRequest();
        request.setTranslations(List.of(english, arabic));
        return request;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void actingAsAdmin() {
        UserPrincipal principal = UserPrincipal.create(userRepository.findById(admin.getId()).orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Map<String, Object> latestRow(AuditAction action, Lookup lookup, Long entityId) {
        return jdbcTemplate.queryForMap(
                "SELECT user_id, user_email, entity_type, entity_slug, old_values, new_values "
                        + "FROM public.audit_logs WHERE action = ? AND entity_type = ? AND entity_id = ? "
                        + "AND success = true ORDER BY id DESC LIMIT 1",
                action.name(), lookup.entityType, entityId);
    }

    private static Map<String, Object> oldValues(Map<String, Object> row) {
        return readJson((String) row.get("old_values"));
    }

    private static Map<String, Object> newValues(Map<String, Object> row) {
        return readJson((String) row.get("new_values"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readJson(String json) {
        if (json == null) {
            return Map.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            throw new AssertionError("the audit row's JSON does not parse: " + json, e);
        }
    }

    private void deleteFixtures() {
        String slugs = "auditcov-lookup-" + suffix + "%";
        jdbcTemplate.update("DELETE FROM public.audit_logs WHERE user_email = ?",
                "auditcov-lookup-admin-" + suffix + "@example.test");

        // Child rows first: the translation tables carry the FK and do not cascade.
        for (Lookup lookup : Lookup.values()) {
            jdbcTemplate.update("DELETE FROM " + lookup.translationTable
                    + " WHERE " + lookup.foreignKey + " IN (SELECT id FROM " + lookup.table
                    + " WHERE slug LIKE ?)", slugs);
        }
        // A farm points at an origin: farms first, then their origins.
        jdbcTemplate.update("DELETE FROM farms WHERE slug LIKE ?", slugs);
        jdbcTemplate.update("DELETE FROM origins WHERE slug LIKE ?", slugs);
        for (Lookup lookup : Lookup.values()) {
            if (lookup == Lookup.FARM || lookup == Lookup.ORIGIN) {
                continue;
            }
            jdbcTemplate.update("DELETE FROM " + lookup.table + " WHERE slug LIKE ?", slugs);
        }
        jdbcTemplate.update("DELETE FROM users WHERE username LIKE ?", "auditcov-lookup-%");
    }
}
