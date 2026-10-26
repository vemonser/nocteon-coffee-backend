package com.nocteon.nocteon_api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.brewingMethod.dto.request.BrewingMethodRequest;
import com.nocteon.nocteon_api.brewingMethod.dto.request.BrewingMethodTranslationRequest;
import com.nocteon.nocteon_api.brewingMethod.dto.response.BrewingMethodResponseDashboard;
import com.nocteon.nocteon_api.brewingMethod.service.BrewingMethodService;
import com.nocteon.nocteon_api.coffeeVariety.dto.request.CoffeeVarietyRequest;
import com.nocteon.nocteon_api.coffeeVariety.dto.request.CoffeeVarietyTranslationRequest;
import com.nocteon.nocteon_api.coffeeVariety.dto.response.CoffeeVarietyResponseDashboard;
import com.nocteon.nocteon_api.coffeeVariety.service.CoffeeVarietyService;
import com.nocteon.nocteon_api.common.exception.product.DuplicateTranslationException;
import com.nocteon.nocteon_api.pairing.dto.request.PairingRequest;
import com.nocteon.nocteon_api.pairing.dto.request.PairingTranslationRequest;
import com.nocteon.nocteon_api.pairing.dto.response.PairingResponseDashboard;
import com.nocteon.nocteon_api.pairing.service.PairingService;
import com.nocteon.nocteon_api.processingMethod.dto.request.ProcessingMethodRequest;
import com.nocteon.nocteon_api.processingMethod.dto.request.ProcessingMethodTranslationRequest;
import com.nocteon.nocteon_api.processingMethod.dto.response.ProcessingMethodResponseDashboard;
import com.nocteon.nocteon_api.processingMethod.service.ProcessingMethodService;
import com.nocteon.nocteon_api.roastLevel.dto.request.RoastLevelRequest;
import com.nocteon.nocteon_api.roastLevel.dto.request.RoastLevelTranslationRequest;
import com.nocteon.nocteon_api.roastLevel.dto.response.DashboardRoastLevelResponse;
import com.nocteon.nocteon_api.roastLevel.service.RoastLevelService;
import com.nocteon.nocteon_api.support.TestJwtKeys;
import com.nocteon.nocteon_api.tastingNote.dto.request.TastingNoteRequest;
import com.nocteon.nocteon_api.tastingNote.dto.request.TastingNoteTranslationRequest;
import com.nocteon.nocteon_api.tastingNote.dto.response.TastingNoteResponseDashboard;
import com.nocteon.nocteon_api.tastingNote.service.TastingNoteService;

/**
 * Proves the two behaviours that depend on soft-deleted rows being invisible to the
 * duplicate-name guard but still owning their UNIQUE {@code slug}:
 *
 * <ul>
 *   <li>reusing a trashed name succeeds and produces a suffixed slug (no constraint 500),</li>
 *   <li>restoring a trashed row whose name is live again returns a duplicate conflict (409).</li>
 * </ul>
 *
 * <p>Origin and Farm have dedicated classes; this covers the remaining six lookup entities.
 */
@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LookupSlugReuseAndRestoreIntegrationTest {

    @Autowired
    private BrewingMethodService brewingMethodService;

    @Autowired
    private CoffeeVarietyService coffeeVarietyService;

    @Autowired
    private ProcessingMethodService processingMethodService;

    @Autowired
    private RoastLevelService roastLevelService;

    @Autowired
    private TastingNoteService tastingNoteService;

    @Autowired
    private PairingService pairingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    private record Element(Long id, String slug) {
    }

    private interface EntityFixture {
        Element create(String en, String ar);

        void delete(String slug);

        void restore(Long id);

        void hardDeleteByName(String en);
    }

    private void hardDeleteByName(String table, String translationTable, String fk, String en) {
        List<Long> ids = jdbcTemplate.queryForList(
                "select e.id from " + table + " e join " + translationTable + " t on t." + fk + " = e.id " +
                        "where t.language = 'en' and lower(trim(t.name)) = lower(trim(?))",
                Long.class, en);
        for (Long id : ids) {
            jdbcTemplate.update("delete from " + translationTable + " where " + fk + " = ?", id);
            jdbcTemplate.update("delete from " + table + " where id = ?", id);
        }
    }

    private BrewingMethodRequest brewingRequest(String en, String ar) {
        BrewingMethodTranslationRequest enT = new BrewingMethodTranslationRequest();
        enT.setLanguage("en");
        enT.setName(en);
        BrewingMethodTranslationRequest arT = new BrewingMethodTranslationRequest();
        arT.setLanguage("ar");
        arT.setName(ar);
        BrewingMethodRequest req = new BrewingMethodRequest();
        req.setTranslations(List.of(enT, arT));
        return req;
    }

    private CoffeeVarietyRequest coffeeVarietyRequest(String en, String ar) {
        CoffeeVarietyTranslationRequest enT = new CoffeeVarietyTranslationRequest();
        enT.setLanguage("en");
        enT.setName(en);
        CoffeeVarietyTranslationRequest arT = new CoffeeVarietyTranslationRequest();
        arT.setLanguage("ar");
        arT.setName(ar);
        CoffeeVarietyRequest req = new CoffeeVarietyRequest();
        req.setTranslations(List.of(enT, arT));
        return req;
    }

    private ProcessingMethodRequest processingMethodRequest(String en, String ar) {
        ProcessingMethodTranslationRequest enT = new ProcessingMethodTranslationRequest();
        enT.setLanguage("en");
        enT.setName(en);
        ProcessingMethodTranslationRequest arT = new ProcessingMethodTranslationRequest();
        arT.setLanguage("ar");
        arT.setName(ar);
        ProcessingMethodRequest req = new ProcessingMethodRequest();
        req.setTranslations(List.of(enT, arT));
        return req;
    }

    private RoastLevelRequest roastLevelRequest(String en, String ar) {
        RoastLevelTranslationRequest enT = new RoastLevelTranslationRequest();
        enT.setLanguage("en");
        enT.setName(en);
        RoastLevelTranslationRequest arT = new RoastLevelTranslationRequest();
        arT.setLanguage("ar");
        arT.setName(ar);
        RoastLevelRequest req = new RoastLevelRequest();
        req.setColor("FFFFFF");
        req.setTranslations(List.of(enT, arT));
        return req;
    }

    private TastingNoteRequest tastingNoteRequest(String en, String ar) {
        TastingNoteTranslationRequest enT = new TastingNoteTranslationRequest();
        enT.setLanguage("en");
        enT.setName(en);
        TastingNoteTranslationRequest arT = new TastingNoteTranslationRequest();
        arT.setLanguage("ar");
        arT.setName(ar);
        TastingNoteRequest req = new TastingNoteRequest();
        req.setTranslations(List.of(enT, arT));
        return req;
    }

    private PairingRequest pairingRequest(String en, String ar) {
        PairingTranslationRequest enT = new PairingTranslationRequest();
        enT.setLanguage("en");
        enT.setName(en);
        PairingTranslationRequest arT = new PairingTranslationRequest();
        arT.setLanguage("ar");
        arT.setName(ar);
        PairingRequest req = new PairingRequest();
        req.setTranslations(List.of(enT, arT));
        return req;
    }

    private EntityFixture brewingMethodFixture() {
        return new EntityFixture() {
            public Element create(String en, String ar) {
                BrewingMethodResponseDashboard r = brewingMethodService.create(brewingRequest(en, ar));
                return new Element(r.getId(), r.getSlug());
            }

            public void delete(String slug) {
                brewingMethodService.delete(slug);
            }

            public void restore(Long id) {
                brewingMethodService.restore(id);
            }

            public void hardDeleteByName(String en) {
                LookupSlugReuseAndRestoreIntegrationTest.this
                        .hardDeleteByName("brewing_methods", "brewing_method_translations", "brewing_method_id", en);
            }
        };
    }

    private EntityFixture coffeeVarietyFixture() {
        return new EntityFixture() {
            public Element create(String en, String ar) {
                CoffeeVarietyResponseDashboard r = coffeeVarietyService.create(coffeeVarietyRequest(en, ar));
                return new Element(r.getId(), r.getSlug());
            }

            public void delete(String slug) {
                coffeeVarietyService.delete(slug);
            }

            public void restore(Long id) {
                coffeeVarietyService.restore(id);
            }

            public void hardDeleteByName(String en) {
                LookupSlugReuseAndRestoreIntegrationTest.this
                        .hardDeleteByName("coffee_varieties", "coffee_variety_translations", "coffee_variety_id", en);
            }
        };
    }

    private EntityFixture processingMethodFixture() {
        return new EntityFixture() {
            public Element create(String en, String ar) {
                ProcessingMethodResponseDashboard r = processingMethodService.create(processingMethodRequest(en, ar));
                return new Element(r.getId(), r.getSlug());
            }

            public void delete(String slug) {
                processingMethodService.delete(slug);
            }

            public void restore(Long id) {
                processingMethodService.restore(id);
            }

            public void hardDeleteByName(String en) {
                LookupSlugReuseAndRestoreIntegrationTest.this
                        .hardDeleteByName("processing_methods", "processing_method_translations", "processing_method_id", en);
            }
        };
    }

    private EntityFixture roastLevelFixture() {
        return new EntityFixture() {
            public Element create(String en, String ar) {
                DashboardRoastLevelResponse r = roastLevelService.create(roastLevelRequest(en, ar));
                return new Element(r.getId(), r.getSlug());
            }

            public void delete(String slug) {
                roastLevelService.delete(slug);
            }

            public void restore(Long id) {
                roastLevelService.restore(id);
            }

            public void hardDeleteByName(String en) {
                LookupSlugReuseAndRestoreIntegrationTest.this
                        .hardDeleteByName("roast_levels", "roast_level_translations", "roast_level_id", en);
            }
        };
    }

    private EntityFixture tastingNoteFixture() {
        return new EntityFixture() {
            public Element create(String en, String ar) {
                TastingNoteResponseDashboard r = tastingNoteService.create(tastingNoteRequest(en, ar));
                return new Element(r.getId(), r.getSlug());
            }

            public void delete(String slug) {
                tastingNoteService.delete(slug);
            }

            public void restore(Long id) {
                tastingNoteService.restore(id);
            }

            public void hardDeleteByName(String en) {
                LookupSlugReuseAndRestoreIntegrationTest.this
                        .hardDeleteByName("tasting_notes", "tasting_note_translations", "tasting_note_id", en);
            }
        };
    }

    private EntityFixture pairingFixture() {
        return new EntityFixture() {
            public Element create(String en, String ar) {
                PairingResponseDashboard r = pairingService.create(pairingRequest(en, ar), null);
                return new Element(r.getId(), r.getSlug());
            }

            public void delete(String slug) {
                pairingService.delete(slug);
            }

            public void restore(Long id) {
                pairingService.restore(id);
            }

            public void hardDeleteByName(String en) {
                LookupSlugReuseAndRestoreIntegrationTest.this
                        .hardDeleteByName("pairings", "pairing_translations", "pairing_id", en);
            }
        };
    }

    private void assertReuseAndRestore(EntityFixture fixture, String label) {
        String en = "ZZ Six " + label + " Reuse";
        String ar = "zz ست " + label;
        fixture.hardDeleteByName(en);
        try {
            Element first = fixture.create(en, ar);
            fixture.delete(first.slug());

            Element reused = fixture.create(en, ar);
            assertThat(reused.id()).isNotEqualTo(first.id());
            assertThat(reused.slug()).isNotEqualTo(first.slug());

            assertThatThrownBy(() -> fixture.create(en, ar))
                    .isInstanceOf(DuplicateTranslationException.class);

            fixture.delete(reused.slug());
            Element live = fixture.create(en, ar + " مباشر");
            assertThat(live.id()).isNotEqualTo(first.id());

            assertThatThrownBy(() -> fixture.restore(reused.id()))
                    .isInstanceOf(DuplicateTranslationException.class);
        } finally {
            fixture.hardDeleteByName(en);
        }
    }

    @Test
    void brewingMethodReuseAndRestore() {
        assertReuseAndRestore(brewingMethodFixture(), "brewing");
    }

    @Test
    void coffeeVarietyReuseAndRestore() {
        assertReuseAndRestore(coffeeVarietyFixture(), "variety");
    }

    @Test
    void processingMethodReuseAndRestore() {
        assertReuseAndRestore(processingMethodFixture(), "processing");
    }

    @Test
    void roastLevelReuseAndRestore() {
        assertReuseAndRestore(roastLevelFixture(), "roast");
    }

    @Test
    void tastingNoteReuseAndRestore() {
        assertReuseAndRestore(tastingNoteFixture(), "tasting");
    }

    @Test
    void pairingReuseAndRestore() {
        assertReuseAndRestore(pairingFixture(), "pairing");
    }
}
