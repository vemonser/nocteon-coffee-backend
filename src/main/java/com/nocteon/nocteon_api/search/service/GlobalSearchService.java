package com.nocteon.nocteon_api.search.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.category.entity.CategoryTranslation;
import com.nocteon.nocteon_api.category.repository.CategoryRepository;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.journal.entity.JournalPostTranslation;
import com.nocteon.nocteon_api.journal.repository.JournalPostRepository;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.promoCode.repository.PromoCodeRepository;
import com.nocteon.nocteon_api.search.dto.response.GlobalSearchResponse;
import com.nocteon.nocteon_api.search.dto.response.SearchResultDto;
import com.nocteon.nocteon_api.search.enums.SearchResultType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GlobalSearchService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final JournalPostRepository journalPostRepository;

    private static final int RESULTS_PER_TYPE = 5;
    private static final int MIN_QUERY_LENGTH = 2;

    /**
     * Search every dashboard entity type at once.
     *
     * <p>Matching is already bilingual: the translatable types inner-join their translations with no
     * language predicate, so an Arabic or an English query both match, and users are matched with
     * {@code ILIKE} on first and last name. Nothing here narrows by
     * {@link LocaleContextHolder}, because an admin searching for "قهوة" must find the product
     * whose Arabic name contains it even while the dashboard itself is in English.
     *
     * <p>What the locale <em>does</em> decide is which translation is shown as the result title.
     * That is presentation, not matching: the current locale wins, falling back to whichever
     * translation exists first.
     *
     * <p>{@code totalCount} is the number matched, not the number returned. Each type is capped at
     * {@link #RESULTS_PER_TYPE} rows, so reporting {@code results.size()} claimed five matches for
     * a query that hit twelve and hid the rest. The counts come from dedicated count queries that
     * mirror the row queries' predicates.
     */
    @Transactional(readOnly = true)
    public GlobalSearchResponse search(String query) {
        if (query == null || query.trim().length() < MIN_QUERY_LENGTH) {
            return GlobalSearchResponse.builder()
                    .results(List.of())
                    .totalCount(0)
                    .countsByType(Map.of())
                    .build();
        }

        String trimmedQuery = query.trim();
        // Deliberately unsorted: every row query carries its own relevance ORDER BY. Passing a sort
        // here would make Spring Data append a second ORDER BY clause, which is a SQL error.
        Pageable limit = PageRequest.of(0, RESULTS_PER_TYPE);

        List<SearchResultDto> results = new ArrayList<>();
        results.addAll(searchOrders(trimmedQuery, limit));
        results.addAll(searchUsers(trimmedQuery, limit));
        results.addAll(searchProducts(trimmedQuery, limit));
        results.addAll(searchCategories(trimmedQuery, limit));
        results.addAll(searchPromoCodes(trimmedQuery, limit));
        results.addAll(searchJournalPosts(trimmedQuery, limit));

        Map<String, Integer> countsByType = new LinkedHashMap<>();
        countsByType.put(SearchResultType.ORDER.getRouteSegment(),
                toCount(orderRepository.countSearchOrders(trimmedQuery)));
        countsByType.put(SearchResultType.USER.getRouteSegment(),
                toCount(userRepository.countSearchUsers(trimmedQuery)));
        countsByType.put(SearchResultType.PRODUCT.getRouteSegment(),
                toCount(productRepository.countSearchProducts(trimmedQuery)));
        countsByType.put(SearchResultType.CATEGORY.getRouteSegment(),
                toCount(categoryRepository.countSearchCategories(trimmedQuery)));
        countsByType.put(SearchResultType.PROMO_CODE.getRouteSegment(),
                toCount(promoCodeRepository.countSearchPromoCodes(trimmedQuery)));
        countsByType.put(SearchResultType.JOURNAL_POST.getRouteSegment(),
                toCount(journalPostRepository.countSearchJournalPosts(trimmedQuery)));

        return GlobalSearchResponse.builder()
                .results(results)
                .totalCount(countsByType.values().stream().mapToInt(Integer::intValue).sum())
                .countsByType(countsByType)
                .build();
    }

    /** Narrows a count to {@code int} so a pathological match set cannot overflow the DTO. */
    private static int toCount(long count) {
        return (int) Math.min(count, Integer.MAX_VALUE);
    }

    private List<SearchResultDto> searchOrders(String query, Pageable limit) {
        return orderRepository.searchOrders(query, limit).stream()
                .map(o -> SearchResultDto.builder()
                        .type(SearchResultType.ORDER)
                        .id(o.getId())
                        .title("Order #" + o.getId())
                        .subtitle(o.getUser().getEmail() + " · " + o.getStatus())
                        .identifier(String.valueOf(o.getId()))
                        .build())
                .toList();
    }

    private List<SearchResultDto> searchUsers(String query, Pageable limit) {
        return userRepository.searchUsers(query, limit).stream()
                .map(u -> SearchResultDto.builder()
                        .type(SearchResultType.USER)
                        .id(u.getId())
                        .title(u.getProfile().getFullName())
                        .subtitle(u.getEmail())
                        .identifier(u.getId().toString())
                        .build())
                .toList();
    }

    private List<SearchResultDto> searchProducts(String query, Pageable limit) {
        String language = LocaleContextHolder.getLocale().getLanguage();

        return productRepository.searchProducts(query, limit).stream()
                .map(p -> {
                    ProductTranslation translation = p.getTranslations().stream()
                            .filter(t -> t.getLanguage().equals(language))
                            .findFirst()
                            .orElseGet(() -> p.getTranslations().stream().findFirst().orElse(null));

                    return SearchResultDto.builder()
                            .type(SearchResultType.PRODUCT)
                            .id(p.getId())
                            .title(translation != null ? translation.getName() : p.getSlug())
                            .subtitle(p.getCategory().getSlug())
                            .identifier(p.getSlug())
                            .build();
                })
                .toList();
    }

    private List<SearchResultDto> searchPromoCodes(String query, Pageable limit) {
        return promoCodeRepository.searchPromoCodes(query, limit).stream()
                .map(pc -> SearchResultDto.builder()
                        .type(SearchResultType.PROMO_CODE)
                        .id(pc.getId())
                        .title(pc.getCode())
                        .subtitle(pc.getDiscountType() + " · " + (pc.isActive() ? "Active" : "Inactive"))
                        .identifier(pc.getCode())
                        .build())
                .toList();
    }
    private List<SearchResultDto> searchCategories(String query, Pageable limit) {
        String language = LocaleContextHolder.getLocale().getLanguage();

        return categoryRepository.searchCategories(query, limit).stream()
                .map(c -> {
                    CategoryTranslation translation = c.getTranslations().stream()
                            .filter(t -> t.getLanguage().equals(language))
                            .findFirst()
                            .orElseGet(() -> c.getTranslations().stream().findFirst().orElse(null));

                    return SearchResultDto.builder()
                            .type(SearchResultType.CATEGORY)
                            .id(c.getId())
                            .title(translation != null ? translation.getName() : c.getSlug())
                            .subtitle(Boolean.TRUE.equals(c.getIsActive()) ? "Active" : "Inactive")
                            .identifier(c.getSlug())
                            .build();
                })
                .toList();
    }


    private List<SearchResultDto> searchJournalPosts(String query, Pageable limit) {
        String language = LocaleContextHolder.getLocale().getLanguage();

        return journalPostRepository.searchJournalPosts(query, limit).stream()
                .map(jp -> {
                    JournalPostTranslation translation = jp.getTranslations().stream()
                            .filter(t -> t.getLanguage().equals(language))
                            .findFirst()
                            .orElseGet(() -> jp.getTranslations().stream().findFirst().orElse(null));

                    return SearchResultDto.builder()
                            .type(SearchResultType.JOURNAL_POST)
                            .id(jp.getId())
                            .title(translation != null ? translation.getTitle() : jp.getSlug())
                            .subtitle(jp.isFeatured() ? "Featured" : null)
                            .identifier(jp.getSlug())
                            .build();
                })
                .toList();
    }
}