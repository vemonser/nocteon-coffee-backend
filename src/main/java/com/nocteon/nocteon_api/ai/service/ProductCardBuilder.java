package com.nocteon.nocteon_api.ai.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.StringUtils;

import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.mapper.ProductResponseMapper;
import com.nocteon.nocteon_api.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns the slugs a search returned into the cards the storefront renders beside the reply.
 *
 * <h2>Why the ranking survives the read</h2>
 * The order the tools returned is the model's evidence trail — it is why it cited those coffees
 * and not others. The database returns rows in whatever order it likes, so the order is rebuilt
 * here by walking the wanted slugs and looking each one up, rather than by sorting the result set.
 *
 * <h2>Why unusable cards are dropped rather than rendered</h2>
 * Three things are dropped: a slug the catalog no longer holds, a product that is no longer on
 * sale, and a card the mapper cannot project. All three would otherwise reach the client as a
 * blank tile or a broken recommendation — and a shopper acting on a delisted coffee is exactly the
 * failure this rail exists to avoid.
 *
 * <h2>Why the slug lookup runs through {@link TransactionOperations}</h2>
 * A transaction callback rather than {@code @Transactional} because the caller reaches this class
 * from a tool method Spring AI invokes reflectively on a raw instance, not through a Spring proxy,
 * where an annotation would silently do nothing.
 *
 * <h2>Why the lookup join-fetches {@code category} and nothing else</h2>
 * {@code buildListResponse} reads four relations per product: {@code category},
 * {@code translations}, {@code media} and {@code variants}. Three of them are
 * {@code @BatchSize(30)} collections and batch-load in three statements however many products are
 * asked for. {@code category} is a plain lazy {@code @ManyToOne}, so left lazy it cost one extra
 * {@code SELECT} per card — up to {@link #MAX_CARDS} on every chat turn, for one slug column.
 * {@code findBySlugInWithCategory} fetches it, and only it: adding the three collections to the
 * same query would satisfy all four relations at once and cross-multiply the result by the product
 * of their sizes. Fetching the collections' sibling to-one multiplies nothing, because each
 * product matches at most one category.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductCardBuilder {

    /** Ceiling on the product rail attached to a reply. */
    private static final int MAX_CARDS = 8;

    private final ProductRepository productRepository;
    private final ProductResponseMapper productResponseMapper;
    private final TransactionOperations transactions;

    /**
     * Resolves slugs into cards, in the order they were given.
     *
     * <p>Never throws: a failed catalog read costs the shopper the pictures, not the advice, so
     * this returns an empty rail and the reply still goes out.
     *
     * @param slugs    slugs returned by {@code search_products} during this turn, or {@code null}
     * @param language the language the card names should be rendered in
     * @return at most {@link #MAX_CARDS} cards; empty when there is nothing to show, never null
     */
    public List<ProductCardResponse> cardsFor(List<String> slugs, String language) {
        try {
            if (slugs == null || slugs.isEmpty()) {
                return List.of();
            }

            List<String> wanted = slugs.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .limit(MAX_CARDS)
                    .toList();
            if (wanted.isEmpty()) {
                return List.of();
            }

            Map<String, Product> bySlug = new LinkedHashMap<>();
            inTransaction(() -> productRepository.findBySlugInWithCategory(wanted)).stream()
                    .filter(Product::isActive)
                    .filter(product -> StringUtils.hasText(product.getSlug()))
                    .forEach(product -> bySlug.putIfAbsent(product.getSlug(), product));

            List<ProductCardResponse> cards = new ArrayList<>();
            for (String slug : wanted) {
                Product product = bySlug.get(slug);
                if (product == null) {
                    continue;
                }
                // A card the mapper cannot project — a product whose category is missing from the
                // catalog — is skipped rather than appended as a null the client would render as a
                // broken tile.
                ProductCardResponse card = productResponseMapper.buildListResponse(product, language);
                if (card != null) {
                    cards.add(card);
                }
            }
            return cards;
        } catch (Exception e) {
            log.warn("Product cards unavailable, replying without them: {}", e.getMessage());
            return List.of();
        }
    }

    /** Runs a catalog read with a persistence context open, and nothing else. */
    private <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }
}