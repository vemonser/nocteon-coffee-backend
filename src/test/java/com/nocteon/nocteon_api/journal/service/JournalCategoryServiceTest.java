package com.nocteon.nocteon_api.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.common.exception.invalid.JournalCategoryInUseException;
import com.nocteon.nocteon_api.common.exception.invalid.JournalCategorySlugTakenException;
import com.nocteon.nocteon_api.common.service.HtmlSanitizer;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.journal.dto.request.JournalCategoryRequest;
import com.nocteon.nocteon_api.journal.dto.request.JournalCategoryTranslationRequest;
import com.nocteon.nocteon_api.journal.dto.response.JournalCategoryResponseDashboard;
import com.nocteon.nocteon_api.journal.entity.JournalCategory;
import com.nocteon.nocteon_api.journal.entity.JournalCategoryTranslation;
import com.nocteon.nocteon_api.journal.repository.JournalCategoryRepository;
import com.nocteon.nocteon_api.journal.repository.JournalPostRepository;
import com.nocteon.nocteon_api.journal.repository.JournalPostTranslationRepository;
import com.nocteon.nocteon_api.product.repository.ProductRepository;

/** FE-05: categories can be created, renamed, and only removed while unused. */
@ExtendWith(MockitoExtension.class)
class JournalCategoryServiceTest {

    @Mock
    private JournalPostRepository postRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private JournalPostTranslationRepository translationRepository;
    @Mock
    private JournalCategoryRepository categoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LookupServiceHelper helper;
    @Mock
    private HtmlSanitizer htmlSanitizer;

    @InjectMocks
    private JournalService service;

    @Test
    void createsACategoryWithItsTranslationsAndAGeneratedSlug() {
        when(helper.generateUniqueSlug(anyString(), any())).thenReturn("brewing-guides");
        when(categoryRepository.save(any(JournalCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        JournalCategoryResponseDashboard created = service.createCategory(
                request("Brewing guides", "أدلة التحضير"));

        assertThat(created.getSlug()).isEqualTo("brewing-guides");
        assertThat(created.getTranslations())
                .extracting(t -> t.getTitle())
                .containsExactly("Brewing guides", "أدلة التحضير");

        ArgumentCaptor<JournalCategory> saved = ArgumentCaptor.forClass(JournalCategory.class);
        verify(categoryRepository).save(saved.capture());
        assertThat(saved.getValue().getTranslations()).hasSize(2);
    }

    @Test
    void theSlugUniquenessCheckIncludesTrashedCategories() {
        when(helper.generateUniqueSlug(anyString(), any())).thenReturn("brewing-guides");
        when(categoryRepository.save(any(JournalCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryRepository.existsBySlugIncludingDeleted("brewing-guides")).thenReturn(true);

        service.createCategory(request("Brewing guides", "أدلة التحضير"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.function.Predicate<String>> predicate =
                ArgumentCaptor.forClass(java.util.function.Predicate.class);
        verify(helper).generateUniqueSlug(eq("Brewing guides"), predicate.capture());
        assertThat(predicate.getValue().test("brewing-guides")).isTrue();
        verify(helper).acquireNameLock("journal-category", "slug", "brewing-guides");
    }

    @Test
    void restoringIsRefusedWhenALiveCategoryTookTheSlug() {
        JournalCategory trashed = JournalCategory.builder().id(9L).slug("guides").build();
        when(categoryRepository.findDeletedById(9L)).thenReturn(Optional.of(trashed));
        when(categoryRepository.existsBySlug("guides")).thenReturn(true);

        assertThatThrownBy(() -> service.restoreCategory(9L))
                .isInstanceOf(JournalCategorySlugTakenException.class);
        assertThat(trashed.getDeletedAt()).isNull();
    }

    @Test
    void restoringSucceedsWhenTheSlugIsFree() {
        JournalCategory trashed = JournalCategory.builder().id(9L).slug("guides").build();
        when(categoryRepository.findDeletedById(9L)).thenReturn(Optional.of(trashed));
        when(categoryRepository.existsBySlug("guides")).thenReturn(false);
        when(categoryRepository.save(trashed)).thenReturn(trashed);

        service.restoreCategory(9L);

        assertThat(trashed.getDeletedAt()).isNull();
        verify(categoryRepository).save(trashed);
    }

    @Test
    void renamesTheExistingTranslationAndAddsTheMissingLanguage() {
        JournalCategory category = JournalCategory.builder().id(3L).slug("guides").build();
        category.getTranslations().add(JournalCategoryTranslation.builder()
                .journalCategory(category).language("en").name("Old name").build());
        when(categoryRepository.findByIdWithTranslations(3L)).thenReturn(Optional.of(category));
        when(categoryRepository.save(category)).thenReturn(category);

        service.renameCategory(3L, request("New name", "اسم جديد"));

        assertThat(category.getTranslations())
                .extracting(JournalCategoryTranslation::getName)
                .containsExactlyInAnyOrder("New name", "اسم جديد");
    }

    @Test
    void refusesToDeleteACategoryThatStillHasPostsAndReportsTheCount() {
        JournalCategory category = JournalCategory.builder().id(3L).slug("guides").build();
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(category));
        when(postRepository.countAllByCategoryIdIncludingDeleted(3L)).thenReturn(4L);

        assertThatThrownBy(() -> service.deleteCategory(3L))
                .isInstanceOfSatisfying(JournalCategoryInUseException.class,
                        ex -> assertThat(ex.getPostCount()).isEqualTo(4L));
        assertThat(category.getDeletedAt()).isNull();
    }

    @Test
    void softDeletesAnUnusedCategory() {
        JournalCategory category = JournalCategory.builder().id(3L).slug("guides").build();
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(category));
        when(postRepository.countAllByCategoryIdIncludingDeleted(3L)).thenReturn(0L);
        when(categoryRepository.save(category)).thenReturn(category);

        service.deleteCategory(3L);

        assertThat(category.getDeletedAt()).isNotNull();
    }

    private static JournalCategoryRequest request(String english, String arabic) {
        JournalCategoryTranslationRequest en = new JournalCategoryTranslationRequest();
        en.setLanguage("en");
        en.setName(english);
        JournalCategoryTranslationRequest ar = new JournalCategoryTranslationRequest();
        ar.setLanguage("ar");
        ar.setName(arabic);

        JournalCategoryRequest request = new JournalCategoryRequest();
        request.setTranslations(List.of(en, ar));
        return request;
    }
}
