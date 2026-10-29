package com.nocteon.nocteon_api.product.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethod;
import com.nocteon.nocteon_api.brewingMethod.repository.BrewingMethodRepository;
import com.nocteon.nocteon_api.common.exception.invalid.RelatedProductInactiveException;
import com.nocteon.nocteon_api.common.exception.invalid.RelatedProductSelfReferenceException;
import com.nocteon.nocteon_api.common.exception.invalid.TooManyRelatedProductsException;
import com.nocteon.nocteon_api.common.exception.notFound.BrewingMethodNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.ProductNotFoundException;
import com.nocteon.nocteon_api.pairing.entity.Pairing;
import com.nocteon.nocteon_api.pairing.repository.PairingRepository;
import com.nocteon.nocteon_api.product.dto.request.ProductBrewingMethodRequest;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductBrewingMethod;
import com.nocteon.nocteon_api.product.entity.ProductRelation;
import com.nocteon.nocteon_api.product.repository.ProductBrewingMethodRepository;
import com.nocteon.nocteon_api.product.repository.ProductRelationRepository;
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
    private final ProductRelationRepository productRelationRepository;
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
     * pick, not a catalogue dump), the product itself (a dead link on its own page), a slug that
     * does not exist (the admin typed it), and a product that is not for sale (the picker only
     * offers active ones — the API is the boundary that enforces it).
     *
     * <p>The rows are replaced wholesale: one delete plus N inserts. Updating positions in place
     * is what Hibernate does for an ordered collection, and it collides on the primary key as soon
     * as two entries swap.
     */
    public void replaceRelatedProducts(Product product, List<String> slugs) {
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
                if (!related.isActive()) {
                    throw new RelatedProductInactiveException();
                }
                resolved.add(related);
            }
        }

        productRelationRepository.deleteByProductId(product.getId());
        productRelationRepository.flush();
        for (int position = 0; position < resolved.size(); position += 1) {
            productRelationRepository.save(ProductRelation.builder()
                    .product(product)
                    .relatedProduct(resolved.get(position))
                    .position(position)
                    .build());
        }
    }

    /** The ordered related products of a product, with their to-one relations fetched. */
    @Transactional(readOnly = true)
    public List<Product> relatedProductsOf(Long productId) {
        return productRelationRepository.findByProductIdOrderByPosition(productId).stream()
                .map(ProductRelation::getRelatedProduct)
                .toList();
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
