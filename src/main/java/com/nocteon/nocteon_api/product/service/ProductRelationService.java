package com.nocteon.nocteon_api.product.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethod;
import com.nocteon.nocteon_api.brewingMethod.repository.BrewingMethodRepository;
import com.nocteon.nocteon_api.common.exception.invalid.RelatedProductSelfReferenceException;
import com.nocteon.nocteon_api.common.exception.invalid.TooManyRelatedProductsException;
import com.nocteon.nocteon_api.common.exception.notFound.BrewingMethodNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.ProductNotFoundException;
import com.nocteon.nocteon_api.pairing.entity.Pairing;
import com.nocteon.nocteon_api.pairing.repository.PairingRepository;
import com.nocteon.nocteon_api.product.dto.request.ProductBrewingMethodRequest;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductBrewingMethod;
import com.nocteon.nocteon_api.product.repository.ProductBrewingMethodRepository;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.tastingNote.entity.TastingNote;
import com.nocteon.nocteon_api.tastingNote.repository.TastingNoteRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductRelationService {
    private final ProductRepository productRepository;
    private final TastingNoteRepository tastingNoteRepository;
    private final PairingRepository pairingRepository;
    private final ProductBrewingMethodRepository productBrewingMethodRepository;
    private final BrewingMethodRepository brewingMethodRepository;

    void attachTastingNotes(Product product, List<String> slugs) {
        if (slugs == null || slugs.isEmpty()) {
            return;
        }
        List<TastingNote> tastingNotes = tastingNoteRepository.findBySlugIn(slugs);
        product.getTastingNotes().addAll(tastingNotes);
        productRepository.save(product);
    }

    void replaceTastingNotes(Product product, List<String> slugs) {
        product.getTastingNotes().clear();
        if (slugs != null && !slugs.isEmpty()) {
            product.getTastingNotes().addAll(tastingNoteRepository.findBySlugIn(slugs));
        }
    }

    void attachPairings(Product product, List<String> slugs) {
        if (slugs == null || slugs.isEmpty()) {
            return;
        }
        List<Pairing> pairings = pairingRepository.findBySlugIn(slugs);
        product.getPairings().addAll(pairings);
        productRepository.save(product);
    }

    void replacePairings(Product product, List<String> slugs) {
        product.getPairings().clear();
        if (slugs != null && !slugs.isEmpty()) {
            product.getPairings().addAll(pairingRepository.findBySlugIn(slugs));
        }
    }

    void saveBrewingMethods(Product product, List<ProductBrewingMethodRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        for (ProductBrewingMethodRequest bm : requests) {
            productBrewingMethodRepository.save(buildBrewingMethod(product, bm));
        }
    }

    void replaceBrewingMethods(Product product, List<ProductBrewingMethodRequest> requests) {
        productBrewingMethodRepository.deleteByProductId(product.getId());
        productBrewingMethodRepository.flush();
        saveBrewingMethods(product, requests);
    }

    /**
     * FE-11: set "You may also like" to exactly {@code slugs}, keeping the order the admin picked.
     *
     * <p>Three things are refused rather than trimmed: more than six (the list is a merchandising
     * pick, not a catalogue dump), the product itself (a dead link on its own page), and a slug
     * that does not exist (the admin typed something the save would otherwise forget). The
     * collection is mutated in place — Hibernate merges the entity and would clear a replacement.
     */
    void replaceRelatedProducts(Product product, List<String> slugs) {
        List<String> wanted = slugs == null
                ? List.of()
                : slugs.stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .distinct()
                        .toList();

        if (wanted.size() > TooManyRelatedProductsException.MAX_RELATED_PRODUCTS) {
            throw new TooManyRelatedProductsException();
        }
        if (product.getSlug() != null && wanted.contains(product.getSlug())) {
            throw new RelatedProductSelfReferenceException();
        }

        List<Product> resolved = new ArrayList<>();
        if (!wanted.isEmpty()) {
            Map<String, Product> bySlug = productRepository.findBySlugIn(wanted).stream()
                    .collect(Collectors.toMap(Product::getSlug, p -> p));
            for (String slug : wanted) {
                Product related = bySlug.get(slug);
                if (related == null) {
                    throw new ProductNotFoundException();
                }
                resolved.add(related);
            }
        }

        if (product.getRelatedProducts() == null) {
            product.setRelatedProducts(new ArrayList<>());
        }
        product.getRelatedProducts().clear();
        product.getRelatedProducts().addAll(resolved);
    }

    private ProductBrewingMethod buildBrewingMethod(Product product, ProductBrewingMethodRequest bm) {
        BrewingMethod brewingMethod = brewingMethodRepository
                .findBySlug(bm.getBrewingMethodSlug())
                .orElseThrow(BrewingMethodNotFoundException::new);

        return ProductBrewingMethod.builder()
                .product(product)
                .brewingMethod(brewingMethod)
                .score(bm.getScore())
                .build();
    }
}
