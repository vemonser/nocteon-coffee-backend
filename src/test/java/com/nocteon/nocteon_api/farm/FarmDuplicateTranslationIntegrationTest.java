package com.nocteon.nocteon_api.farm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.common.exception.product.DuplicateTranslationException;
import com.nocteon.nocteon_api.farm.dto.request.FarmRequest;
import com.nocteon.nocteon_api.farm.dto.request.FarmTranslationRequest;
import com.nocteon.nocteon_api.farm.dto.response.DashboardFarmResponse;
import com.nocteon.nocteon_api.farm.service.FarmService;
import com.nocteon.nocteon_api.origin.dto.request.OriginRequest;
import com.nocteon.nocteon_api.origin.dto.request.OriginTranslationRequest;
import com.nocteon.nocteon_api.origin.dto.response.DashboardOriginResponse;
import com.nocteon.nocteon_api.origin.service.OriginService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * Targeted coverage for the duplicate live-name guard on farms: create, rename, restore and
 * reuse-after-soft-delete. Each test creates its own throwaway origin fixture because a farm
 * cannot exist without one.
 */
@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FarmDuplicateTranslationIntegrationTest {

    @Autowired
    private FarmService farmService;

    @Autowired
    private OriginService originService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    private OriginTranslationRequest originTranslation(String language, String name, String description) {
        OriginTranslationRequest t = new OriginTranslationRequest();
        t.setLanguage(language);
        t.setName(name);
        t.setDescription(description);
        return t;
    }

    private DashboardOriginResponse createOriginFixture(String suffix) {
        OriginRequest req = new OriginRequest();
        req.setCode("ZZF");
        List<OriginTranslationRequest> ts = new ArrayList<>();
        ts.add(originTranslation("en", "ZZ FarmFixture Origin " + suffix, "desc"));
        ts.add(originTranslation("ar", "zz أصل مزرعة " + suffix, "وصف"));
        req.setTranslations(ts);
        return originService.create(req, null);
    }

    private FarmTranslationRequest farmTranslation(String language, String name, String description) {
        FarmTranslationRequest t = new FarmTranslationRequest();
        t.setLanguage(language);
        t.setName(name);
        t.setDescription(description);
        t.setCountry("ZZ");
        return t;
    }

    private FarmRequest buildRequest(String originSlug, String enName, String arName) {
        FarmRequest req = new FarmRequest();
        req.setOriginSlug(originSlug);
        List<FarmTranslationRequest> ts = new ArrayList<>();
        ts.add(farmTranslation("en", enName, "desc"));
        ts.add(farmTranslation("ar", arName, "وصف"));
        req.setTranslations(ts);
        return req;
    }

    private void hardDeleteFarmById(Long id) {
        if (id == null) {
            return;
        }
        jdbcTemplate.update("delete from farm_translations where farm_id = ?", id);
        jdbcTemplate.update("delete from farms where id = ?", id);
    }

    private void hardDeleteFarmByName(String enName) {
        List<Long> ids = jdbcTemplate.queryForList(
                "select f.id from farms f join farm_translations t on t.farm_id = f.id " +
                        "where t.language = 'en' and lower(trim(t.name)) = lower(trim(?))",
                Long.class, enName);
        ids.forEach(this::hardDeleteFarmById);
    }

    private void hardDeleteOriginById(Long id) {
        if (id == null) {
            return;
        }
        jdbcTemplate.update("delete from farms where origin_id = ?", id);
        jdbcTemplate.update("delete from origin_translations where origin_id = ?", id);
        jdbcTemplate.update("delete from origins where id = ?", id);
    }

    @Test
    void duplicateCreateReturns409() {
        DashboardOriginResponse origin = createOriginFixture("Create");
        String en = "ZZ DupFarm Create";
        String ar = "zz تكرار مزرعة";
        hardDeleteFarmByName(en);
        Long firstId = null;
        try {
            DashboardFarmResponse first = farmService.create(buildRequest(origin.getSlug(), en, ar), null);
            firstId = first.getId();
            assertThatThrownBy(() -> farmService.create(buildRequest(origin.getSlug(), en, ar), null))
                    .isInstanceOf(DuplicateTranslationException.class);
        } finally {
            hardDeleteFarmById(firstId);
            hardDeleteFarmByName(en);
            hardDeleteOriginById(origin.getId());
        }
    }

    @Test
    void caseInsensitiveDuplicateReturns409() {
        DashboardOriginResponse origin = createOriginFixture("Case");
        String en = "ZZ CaseFarm Base";
        String ar = "zz حالة مزرعة";
        hardDeleteFarmByName(en);
        Long firstId = null;
        try {
            DashboardFarmResponse first = farmService.create(buildRequest(origin.getSlug(), en, ar), null);
            firstId = first.getId();
            assertThatThrownBy(() -> farmService.create(buildRequest(origin.getSlug(), en.toLowerCase(), ar), null))
                    .isInstanceOf(DuplicateTranslationException.class);
        } finally {
            hardDeleteFarmById(firstId);
            hardDeleteFarmByName(en);
            hardDeleteOriginById(origin.getId());
        }
    }

    @Test
    void whitespacePaddedDuplicateReturns409() {
        DashboardOriginResponse origin = createOriginFixture("Ws");
        String en = "ZZ WsFarm Base";
        String ar = "zz مسافة مزرعة";
        hardDeleteFarmByName(en);
        Long firstId = null;
        try {
            DashboardFarmResponse first = farmService.create(buildRequest(origin.getSlug(), en, ar), null);
            firstId = first.getId();
            assertThatThrownBy(() -> farmService.create(buildRequest(origin.getSlug(), "\t" + en + "\n", ar + "2"), null))
                    .isInstanceOf(DuplicateTranslationException.class);
        } finally {
            hardDeleteFarmById(firstId);
            hardDeleteFarmByName(en);
            hardDeleteOriginById(origin.getId());
        }
    }

    @Test
    void duplicateRenameReturns409() {
        DashboardOriginResponse origin = createOriginFixture("Rename");
        String base = "ZZ DupFarm RenameBase";
        String other = "ZZ DupFarm RenameOther";
        hardDeleteFarmByName(base);
        hardDeleteFarmByName(other);
        Long idBase = null;
        Long idOther = null;
        try {
            DashboardFarmResponse a = farmService.create(buildRequest(origin.getSlug(), base, "zz أ"), null);
            idBase = a.getId();
            DashboardFarmResponse b = farmService.create(buildRequest(origin.getSlug(), other, "zz ب"), null);
            idOther = b.getId();

            FarmRequest rename = buildRequest(origin.getSlug(), base, "zz ب");
            assertThatThrownBy(() -> farmService.update(b.getSlug(), rename, null))
                    .isInstanceOf(DuplicateTranslationException.class);
        } finally {
            hardDeleteFarmById(idBase);
            hardDeleteFarmById(idOther);
            hardDeleteFarmByName(base);
            hardDeleteFarmByName(other);
            hardDeleteOriginById(origin.getId());
        }
    }

    @Test
    void noOpRenameSucceeds() {
        DashboardOriginResponse origin = createOriginFixture("NoOp");
        String en = "ZZ NoOpFarm Rename";
        String ar = "zz بدون تغيير مزرعة";
        hardDeleteFarmByName(en);
        Long id = null;
        try {
            DashboardFarmResponse created = farmService.create(buildRequest(origin.getSlug(), en, ar), null);
            id = created.getId();

            DashboardFarmResponse updated = farmService.update(
                    created.getSlug(), buildRequest(origin.getSlug(), en, ar), null);

            assertThat(updated).isNotNull();
            assertThat(updated.getId()).isEqualTo(created.getId());
        } finally {
            hardDeleteFarmById(id);
            hardDeleteFarmByName(en);
            hardDeleteOriginById(origin.getId());
        }
    }

    @Test
    void reuseAfterSoftDelete() {
        DashboardOriginResponse origin = createOriginFixture("Reuse");
        String en = "ZZ ReuseFarm After Delete";
        String ar = "zz إعادة مزرعة";
        hardDeleteFarmByName(en);
        Long firstId = null;
        Long secondId = null;
        try {
            DashboardFarmResponse first = farmService.create(buildRequest(origin.getSlug(), en, ar), null);
            firstId = first.getId();
            String firstSlug = first.getSlug();
            farmService.delete(firstSlug);

            DashboardFarmResponse second = farmService.create(buildRequest(origin.getSlug(), en, ar), null);
            secondId = second.getId();

            assertThat(secondId).isNotEqualTo(firstId);
            // the trashed row still owns the original slug, so the new one must be suffixed
            assertThat(second.getSlug()).isNotEqualTo(firstSlug);
        } finally {
            hardDeleteFarmById(firstId);
            hardDeleteFarmById(secondId);
            hardDeleteFarmByName(en);
            hardDeleteOriginById(origin.getId());
        }
    }

    @Test
    void concurrentCreatesWithSlugCollisionProduceDistinctSlugs() throws Exception {
        DashboardOriginResponse origin = createOriginFixture("Slug");
        String en1 = "ZZ SlugFarm Foo";
        String en2 = "ZZ SlugFarm Foo!"; // slugifies to the same base as en1
        String ar = "zz slug مزرعة";
        hardDeleteFarmByName(en1);
        hardDeleteFarmByName(en2);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Object> task1 = () -> {
            start.await();
            return farmService.create(buildRequest(origin.getSlug(), en1, ar), null);
        };
        Callable<Object> task2 = () -> {
            start.await();
            return farmService.create(buildRequest(origin.getSlug(), en2, ar + "2"), null);
        };
        try {
            Future<Object> f1 = pool.submit(task1);
            Future<Object> f2 = pool.submit(task2);
            start.countDown();

            DashboardFarmResponse r1 = (DashboardFarmResponse) f1.get(30, TimeUnit.SECONDS);
            DashboardFarmResponse r2 = (DashboardFarmResponse) f2.get(30, TimeUnit.SECONDS);

            assertThat(Set.of(r1.getSlug(), r2.getSlug()))
                    .contains("zz-slugfarm-foo");
            assertThat(r1.getSlug()).isNotEqualTo(r2.getSlug());

            hardDeleteFarmById(r1.getId());
            hardDeleteFarmById(r2.getId());
        } finally {
            pool.shutdownNow();
            hardDeleteFarmByName(en1);
            hardDeleteFarmByName(en2);
            hardDeleteOriginById(origin.getId());
        }
    }

    @Test
    void restoreCollisionReturns409() {
        DashboardOriginResponse origin = createOriginFixture("Restore");
        String en = "ZZ RestoreFarm Collision";
        String ar = "zz تعارض مزرعة";
        hardDeleteFarmByName(en);
        Long deletedId = null;
        Long liveId = null;
        try {
            DashboardFarmResponse toDelete = farmService.create(buildRequest(origin.getSlug(), en, ar), null);
            deletedId = toDelete.getId();
            farmService.delete(toDelete.getSlug());

            // the name is free once the first row is soft-deleted
            DashboardFarmResponse live = farmService.create(
                    buildRequest(origin.getSlug(), en, "zz مباشر مزرعة"), null);
            liveId = live.getId();

            final Long restoreTarget = deletedId;
            assertThatThrownBy(() -> farmService.restore(restoreTarget))
                    .isInstanceOf(DuplicateTranslationException.class);
        } finally {
            hardDeleteFarmById(deletedId);
            hardDeleteFarmById(liveId);
            hardDeleteFarmByName(en);
            hardDeleteOriginById(origin.getId());
        }
    }
}
