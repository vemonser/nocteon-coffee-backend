package com.nocteon.nocteon_api.product.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethod;
import com.nocteon.nocteon_api.brewingMethod.repository.BrewingMethodRepository;
import com.nocteon.nocteon_api.common.exception.notFound.BrewingMethodNotFoundException;
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
