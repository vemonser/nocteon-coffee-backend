package com.nocteon.nocteon_api.search.service;

import java.util.List;
import java.util.ArrayList;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

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
    private final ProductRepository productRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final JournalPostRepository journalPostRepository;

    private static final int RESULTS_PER_TYPE = 5;
    private static final int MIN_QUERY_LENGTH = 2;

    public GlobalSearchResponse search(String query) {
        if (query == null || query.trim().length() < MIN_QUERY_LENGTH) {
            return GlobalSearchResponse.builder().results(List.of()).totalCount(0).build();
        }

        String trimmedQuery = query.trim();
        Pageable limit = PageRequest.of(0, RESULTS_PER_TYPE);

        List<SearchResultDto> results = new ArrayList<>();
        results.addAll(searchOrders(trimmedQuery, limit));
        results.addAll(searchUsers(trimmedQuery, limit));
        results.addAll(searchProducts(trimmedQuery, limit));
        results.addAll(searchPromoCodes(trimmedQuery, limit));
        results.addAll(searchJournalPosts(trimmedQuery, limit));

        return GlobalSearchResponse.builder()
                .results(results)
                .totalCount(results.size())
                .build();
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
                        .identifier(u.getUsername())
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