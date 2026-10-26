package com.nocteon.nocteon_api.origin;

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
import com.nocteon.nocteon_api.origin.dto.request.OriginRequest;
import com.nocteon.nocteon_api.origin.dto.request.OriginTranslationRequest;
import com.nocteon.nocteon_api.origin.dto.response.DashboardOriginResponse;
import com.nocteon.nocteon_api.origin.service.OriginService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * Targeted coverage for the duplicate live-name guard on origins: create, rename, restore and
 * reuse-after-soft-delete. Mirrors
 * {@code CategoryDuplicateTranslationIntegrationTest}, which owns the shared concurrency coverage.
 */
@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OriginDuplicateTranslationIntegrationTest {

    @Autowired
    private OriginService originService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    private OriginTranslationRequest translation(String language, String name, String description) {
        OriginTranslationRequest t = new OriginTranslationRequest();
        t.setLanguage(language);
        t.setName(name);
        t.setDescription(description);
        return t;
    }

    private OriginRequest buildRequest(String code, String enName, String arName) {
        OriginRequest req = new OriginRequest();
        req.setCode(code);
        List<OriginTranslationRequest> ts = new ArrayList<>();
        ts.add(translation("en", enName, "desc"));
        ts.add(translation("ar", arName, "وصف"));
        req.setTranslations(ts);
        return req;
    }

    private void hardDeleteById(Long id) {
        if (id == null) {
            return;
        }
        // defensive: a stray farm would block the FK
        jdbcTemplate.update("delete from farms where origin_id = ?", id);
        jdbcTemplate.update("delete from origin_translations where origin_id = ?", id);
        jdbcTemplate.update("delete from origins where id = ?", id);
    }

    private void hardDeleteByName(String enName) {
        List<Long> ids = jdbcTemplate.queryForList(
                "select o.id from origins o join origin_translations t on t.origin_id = o.id " +
                        "where t.language = 'en' and lower(trim(t.name)) = lower(trim(?))",
                Long.class, enName);
        ids.forEach(this::hardDeleteById);
    }

    @Test
    void duplicateCreateReturns409() {
        String en = "ZZ DupOrigin Create";
        String ar = "zz تكرار أصل";
        hardDeleteByName(en);
        Long firstId = null;
        try {
            DashboardOriginResponse first = originService.create(buildRequest("ZZD1", en, ar), null);
            firstId = first.getId();
            assertThatThrownBy(() -> originService.create(buildRequest("ZZD2", en, ar), null))
                    .isInstanceOf(DuplicateTranslationException.class);
        } finally {
            hardDeleteById(firstId);
            hardDeleteByName(en);
        }
    }

    @Test
    void caseInsensitiveDuplicateReturns409() {
        String en = "ZZ CaseOrigin Base";
        String ar = "zz حالة أصل";
        hardDeleteByName(en);
        Long firstId = null;
        try {
            DashboardOriginResponse first = originService.create(buildRequest("ZZC1", en, ar), null);
            firstId = first.getId();
            assertThatThrownBy(() -> originService.create(buildRequest("ZZC2", en.toLowerCase(), ar), null))
                    .isInstanceOf(DuplicateTranslationException.class);
        } finally {
            hardDeleteById(firstId);
            hardDeleteByName(en);
        }
    }

    @Test
    void whitespacePaddedDuplicateReturns409() {
        String en = "ZZ WsOrigin Base";
        String ar = "zz مسافة أصل";
        hardDeleteByName(en);
        Long firstId = null;
        try {
            DashboardOriginResponse first = originService.create(buildRequest("ZZW1", en, ar), null);
            firstId = first.getId();
            assertThatThrownBy(() -> originService.create(buildRequest("ZZW2", "\t" + en + "\n", ar + "2"), null))
                    .isInstanceOf(DuplicateTranslationException.class);
        } finally {
            hardDeleteById(firstId);
            hardDeleteByName(en);
        }
    }

    @Test
    void duplicateRenameReturns409() {
        String base = "ZZ DupOrigin RenameBase";
        String other = "ZZ DupOrigin RenameOther";
        hardDeleteByName(base);
        hardDeleteByName(other);
        Long idBase = null;
        Long idOther = null;
        try {
            DashboardOriginResponse a = originService.create(buildRequest("ZZR1", base, "zz أ"), null);
            idBase = a.getId();
            DashboardOriginResponse b = originService.create(buildRequest("ZZR2", other, "zz ب"), null);
            idOther = b.getId();

            OriginRequest rename = buildRequest("ZZR2", base, "zz ب");
            assertThatThrownBy(() -> originService.update(b.getSlug(), rename, null))
                    .isInstanceOf(DuplicateTranslationException.class);
        } finally {
            hardDeleteById(idBase);
            hardDeleteById(idOther);
            hardDeleteByName(base);
            hardDeleteByName(other);
        }
    }

    @Test
    void noOpRenameSucceeds() {
        String en = "ZZ NoOpOrigin Rename";
        String ar = "zz بدون تغيير أصل";
        hardDeleteByName(en);
        Long id = null;
        try {
            DashboardOriginResponse created = originService.create(buildRequest("ZZN1", en, ar), null);
            id = created.getId();

            DashboardOriginResponse updated = originService.update(
                    created.getSlug(), buildRequest("ZZN1", en, ar), null);

            assertThat(updated).isNotNull();
            assertThat(updated.getId()).isEqualTo(created.getId());
        } finally {
            hardDeleteById(id);
            hardDeleteByName(en);
        }
    }

    @Test
    void reuseAfterSoftDelete() {
        String en = "ZZ ReuseOrigin After Delete";
        String ar = "zz إعادة أصل";
        hardDeleteByName(en);
        Long firstId = null;
        Long secondId = null;
        try {
            DashboardOriginResponse first = originService.create(buildRequest("ZZU1", en, ar), null);
            firstId = first.getId();
            String firstSlug = first.getSlug();
            originService.delete(firstSlug);

            DashboardOriginResponse second = originService.create(buildRequest("ZZU2", en, ar), null);
            secondId = second.getId();

            assertThat(secondId).isNotEqualTo(firstId);
            // the trashed row still owns the original slug, so the new one must be suffixed
            assertThat(second.getSlug()).isNotEqualTo(firstSlug);
        } finally {
            hardDeleteById(firstId);
            hardDeleteById(secondId);
            hardDeleteByName(en);
        }
    }

    @Test
    void concurrentCreatesWithSlugCollisionProduceDistinctSlugs() throws Exception {
        String en1 = "ZZ SlugOrigin Foo";
        String en2 = "ZZ SlugOrigin Foo!"; // slugifies to the same base as en1
        String ar = "zz slug أصل";
        hardDeleteByName(en1);
        hardDeleteByName(en2);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Object> task1 = () -> {
            start.await();
            return originService.create(buildRequest("ZZL1", en1, ar), null);
        };
        Callable<Object> task2 = () -> {
            start.await();
            return originService.create(buildRequest("ZZL2", en2, ar + "2"), null);
        };
        try {
            Future<Object> f1 = pool.submit(task1);
            Future<Object> f2 = pool.submit(task2);
            start.countDown();

            DashboardOriginResponse r1 = (DashboardOriginResponse) f1.get(30, TimeUnit.SECONDS);
            DashboardOriginResponse r2 = (DashboardOriginResponse) f2.get(30, TimeUnit.SECONDS);

            assertThat(Set.of(r1.getSlug(), r2.getSlug()))
                    .contains("zz-slugorigin-foo");
            assertThat(r1.getSlug()).isNotEqualTo(r2.getSlug());

            hardDeleteById(r1.getId());
            hardDeleteById(r2.getId());
        } finally {
            pool.shutdownNow();
            hardDeleteByName(en1);
            hardDeleteByName(en2);
        }
    }

    @Test
    void restoreCollisionReturns409() {
        String en = "ZZ RestoreOrigin Collision";
        String ar = "zz تعارض أصل";
        hardDeleteByName(en);
        Long deletedId = null;
        Long liveId = null;
        try {
            DashboardOriginResponse toDelete = originService.create(buildRequest("ZZS1", en, ar), null);
            deletedId = toDelete.getId();
            originService.delete(toDelete.getSlug());

            // the name is free once the first row is soft-deleted
            DashboardOriginResponse live = originService.create(buildRequest("ZZS2", en, "zz مباشر أصل"), null);
            liveId = live.getId();

            final Long restoreTarget = deletedId;
            assertThatThrownBy(() -> originService.restore(restoreTarget))
                    .isInstanceOf(DuplicateTranslationException.class);
        } finally {
            hardDeleteById(deletedId);
            hardDeleteById(liveId);
            hardDeleteByName(en);
        }
    }
}
