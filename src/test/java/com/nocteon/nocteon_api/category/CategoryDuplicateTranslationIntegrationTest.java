package com.nocteon.nocteon_api.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.category.dto.request.CategoryRequest;
import com.nocteon.nocteon_api.category.dto.request.CreateCategoryTranslationRequest;
import com.nocteon.nocteon_api.category.dto.response.DashboardCategoryResponse;
import com.nocteon.nocteon_api.category.service.CategoryService;
import com.nocteon.nocteon_api.common.exception.product.DuplicateTranslationException;
import com.nocteon.nocteon_api.support.TestJwtKeys;

@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CategoryDuplicateTranslationIntegrationTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    private CategoryRequest buildRequest(String enName, String arName) {
        CategoryRequest req = new CategoryRequest();
        req.setIsActive(true);
        List<CreateCategoryTranslationRequest> ts = new ArrayList<>();
        CreateCategoryTranslationRequest en = new CreateCategoryTranslationRequest();
        en.setLanguage("en");
        en.setName(enName);
        en.setDescription("desc");
        ts.add(en);
        CreateCategoryTranslationRequest ar = new CreateCategoryTranslationRequest();
        ar.setLanguage("ar");
        ar.setName(arName);
        ar.setDescription("وصف");
        ts.add(ar);
        req.setTranslations(ts);
        return req;
    }

    private void hardDeleteByName(String enName) {
        List<Long> ids = jdbcTemplate.queryForList(
                "select c.id from categories c join category_translations t on t.category_id=c.id " +
                        "where t.language='en' and lower(trim(t.name))=lower(trim(?))", Long.class, enName);
        for (Long id : ids) {
            jdbcTemplate.update("delete from category_translations where category_id=?", id);
            jdbcTemplate.update("delete from categories where id=?", id);
        }
    }

    private void hardDeleteById(Long id) {
        if (id == null) return;
        jdbcTemplate.update("delete from category_translations where category_id=?", id);
        jdbcTemplate.update("delete from categories where id=?", id);
    }

    @Test
    void duplicateCreateReturns409() {
        String en = "ZZ DupCat Create";
        String ar = "zz تكرار إنشاء";
        hardDeleteByName(en);
        Long firstId = null;
        try {
            DashboardCategoryResponse first = categoryService.create(buildRequest(en, ar), null);
            firstId = first.getId();
            assertThatThrownBy(() -> categoryService.create(buildRequest(en, ar), null))
                    .isInstanceOf(DuplicateTranslationException.class);
        } finally {
            hardDeleteById(firstId);
            hardDeleteByName(en);
        }
    }

    @Test
    void uniqueCreateHasNonSuffixedSlug() {
        String en = "ZZ UniqueCat Slug";
        String ar = "zz فريد slug";
        hardDeleteByName(en);
        Long id = null;
        try {
            DashboardCategoryResponse created = categoryService.create(buildRequest(en, ar), null);
            id = created.getId();
            String expectedSlug = en.toLowerCase().trim().replaceAll("[^a-z0-9\\s-]", "").replaceAll("\\s+", "-");
            String slug = jdbcTemplate.queryForObject("select slug from categories where id=?", String.class, id);
            assertThat(slug).isEqualTo(expectedSlug);
        } finally {
            hardDeleteById(id);
            hardDeleteByName(en);
        }
    }

    @Test
    void duplicateRenameReturns409() {
        String base = "ZZ DupCat RenameBase";
        String other = "ZZ DupCat RenameOther";
        hardDeleteByName(base);
        hardDeleteByName(other);
        Long idBase = null;
        Long idOther = null;
        try {
            DashboardCategoryResponse a = categoryService.create(buildRequest(base, "zz أ"), null);
            idBase = a.getId();
            DashboardCategoryResponse b = categoryService.create(buildRequest(other, "zz ب"), null);
            idOther = b.getId();
            CategoryRequest rename = buildRequest(base, "zz ب");
            assertThatThrownBy(() -> categoryService.update(b.getSlug(), rename, null))
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
        String en = "ZZ NoOp Rename";
        String ar = "zz بدون تغيير";
        hardDeleteByName(en);
        Long id = null;
        try {
            DashboardCategoryResponse created = categoryService.create(buildRequest(en, ar), null);
            id = created.getId();
            CategoryRequest same = buildRequest(en, ar);
            DashboardCategoryResponse updated = categoryService.update(created.getSlug(), same, null);
            assertThat(updated).isNotNull();
        } finally {
            hardDeleteById(id);
            hardDeleteByName(en);
        }
    }

    @Test
    void reuseAfterSoftDelete() {
        String en = "ZZ Reuse After Delete";
        String ar = "zz إعادة استخدام";
        hardDeleteByName(en);
        Long id = null;
        try {
            DashboardCategoryResponse first = categoryService.create(buildRequest(en, ar), null);
            id = first.getId();
            categoryService.delete(first.getSlug());
            // reuse name after soft-delete -> should succeed
            DashboardCategoryResponse second = categoryService.create(buildRequest(en, ar), null);
            assertThat(second.getId()).isNotEqualTo(first.getId());
            // cleanup second
            hardDeleteById(second.getId());
        } finally {
            // ensure restored? hard delete anyway
            jdbcTemplate.update("update categories set deleted_at = null where id = ?", id);
            hardDeleteById(id);
            hardDeleteByName(en);
        }
    }

    @Test
    void restoreCollisionReturns409() {
        String en = "ZZ Restore Collision";
        String ar = "zz تعارض استعادة";
        hardDeleteByName(en);
        Long deletedId = null;
        Long liveId = null;
        try {
            DashboardCategoryResponse toDelete = categoryService.create(buildRequest(en, ar), null);
            deletedId = toDelete.getId();
            categoryService.delete(toDelete.getSlug());

            DashboardCategoryResponse live = categoryService.create(buildRequest(en, "zz مباشر"), null);
            liveId = live.getId();

            final Long restoreTarget = deletedId;
            assertThatThrownBy(() -> categoryService.restore(restoreTarget))
                    .isInstanceOf(DuplicateTranslationException.class);
        } finally {
            hardDeleteById(deletedId);
            hardDeleteById(liveId);
            hardDeleteByName(en);
        }
    }

    @Test
    void concurrentDuplicateCreatesProduceOneSuccessOne409() throws Exception {
        String en = "ZZ Concurrent Dup";
        String ar = "zz متزامن";
        hardDeleteByName(en);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Object> task = () -> {
            start.await();
            try {
                return categoryService.create(buildRequest(en, ar), null);
            } catch (DuplicateTranslationException e) {
                return e;
            }
        };
        try {
            Future<Object> f1 = pool.submit(task);
            Future<Object> f2 = pool.submit(task);
            start.countDown();
            Object r1 = f1.get(30, TimeUnit.SECONDS);
            Object r2 = f2.get(30, TimeUnit.SECONDS);

            int successes = 0;
            int conflicts = 0;
            for (Object r : new Object[]{r1, r2}) {
                if (r instanceof DuplicateTranslationException) conflicts++;
                else successes++;
            }
            assertThat(successes).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);

            // cleanup created
            for (Object r : new Object[]{r1, r2}) {
                if (r instanceof DashboardCategoryResponse d) {
                    hardDeleteById(d.getId());
                }
            }
        } finally {
            pool.shutdownNow();
            hardDeleteByName(en);
        }
    }

    @Test
    void whitespaceDuplicateReturns409() {
        String en = "ZZ WsDup Base";
        String ar = "zz مسافة";
        hardDeleteByName(en);
        Long firstId = null;
        try {
            DashboardCategoryResponse first = categoryService.create(buildRequest(en, ar), null);
            firstId = first.getId();
            // leading/trailing whitespace must not bypass the duplicate rule
            // ar name differs so the only possible duplicate signal is the whitespace-padded en name
            assertThatThrownBy(() -> categoryService.create(buildRequest("  " + en + "  ", ar + "2"), null))
                    .isInstanceOf(DuplicateTranslationException.class);
        } finally {
            hardDeleteById(firstId);
            hardDeleteByName(en);
        }
    }

    @Test
    void caseInsensitiveDuplicateReturns409() {
        String en = "ZZ CaseDup Base";
        String ar = "zz حالة";
        hardDeleteByName(en);
        Long firstId = null;
        try {
            DashboardCategoryResponse first = categoryService.create(buildRequest(en, ar), null);
            firstId = first.getId();
            assertThatThrownBy(() -> categoryService.create(buildRequest(en.toLowerCase(), ar), null))
                    .isInstanceOf(DuplicateTranslationException.class);
        } finally {
            hardDeleteById(firstId);
            hardDeleteByName(en);
        }
    }

    @Test
    void tabNewlineWhitespaceDuplicateReturns409() {
        String en = "ZZ TabNl Base";
        String ar = "zz تبويب";
        hardDeleteByName(en);
        Long firstId = null;
        try {
            DashboardCategoryResponse first = categoryService.create(buildRequest(en, ar), null);
            firstId = first.getId();
            String padded = "\t" + en + "\n";
            assertThatThrownBy(() -> categoryService.create(buildRequest(padded, ar + "2"), null))
                    .isInstanceOf(DuplicateTranslationException.class);
            assertThatThrownBy(() -> categoryService.create(buildRequest("\n" + en, ar + "3"), null))
                    .isInstanceOf(DuplicateTranslationException.class);
            assertThatThrownBy(() -> categoryService.create(buildRequest(en + "\t", ar + "4"), null))
                    .isInstanceOf(DuplicateTranslationException.class);
        } finally {
            hardDeleteById(firstId);
            hardDeleteByName(en);
        }
    }

    @Test
    void updateWithDuplicateNameDoesNotTouchImage() {
        String base = "ZZ ImgDup Base";
        String other = "ZZ ImgDup Other";
        hardDeleteByName(base);
        hardDeleteByName(other);
        Long idBase = null;
        Long idOther = null;
        try {
            DashboardCategoryResponse a = categoryService.create(buildRequest(base, "zz أ"), null);
            idBase = a.getId();
            jdbcTemplate.update("update categories set image_url = ? where id = ?", "https://example.com/old.png", idBase);

            DashboardCategoryResponse b = categoryService.create(buildRequest(other, "zz ب"), null);
            idOther = b.getId();

            MockMultipartFile image = new MockMultipartFile("file", "new.png", "image/png", new byte[]{1, 2, 3});
            CategoryRequest rename = buildRequest(base, "zz ب");
            assertThatThrownBy(() -> categoryService.update(b.getSlug(), rename, image))
                    .isInstanceOf(DuplicateTranslationException.class);

            String urlA = jdbcTemplate.queryForObject("select image_url from categories where id = ?", String.class, idBase);
            assertThat(urlA).isEqualTo("https://example.com/old.png");
        } finally {
            hardDeleteById(idBase);
            hardDeleteById(idOther);
            hardDeleteByName(base);
            hardDeleteByName(other);
        }
    }
}
