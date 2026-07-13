package com.nocteon.nocteon_api.wishlist.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.common.exception.notFound.ProductNotFoundException;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductMedia;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.repository.ProductMediaRepository;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.product.repository.ProductTranslationRepository;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;
import com.nocteon.nocteon_api.wishlist.dto.response.WishlistItemResponse;
import com.nocteon.nocteon_api.wishlist.dto.response.WishlistResponse;
import com.nocteon.nocteon_api.wishlist.entity.Wishlist;
import com.nocteon.nocteon_api.wishlist.entity.WishlistItem;
import com.nocteon.nocteon_api.wishlist.repository.WishlistItemRepository;
import com.nocteon.nocteon_api.wishlist.repository.WishlistRepository;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;
    private final ProductTranslationRepository productTranslationRepository;
    private final ProductMediaRepository productMediaRepository;
    private final ProductVariantRepository variantRepository;

    public WishlistResponse getWishlist(UserPrincipal principal) {
        Wishlist wishlist = getOrCreateWishlist(principal.getUserId());
        return buildResponse(wishlist);
    }

    @Transactional
    public WishlistResponse addItem(String productSlug, UserPrincipal principal) {
        Wishlist wishlist = getOrCreateWishlist(principal.getUserId());

        if (wishlistItemRepository.existsByWishlistIdAndProductSlug(
                wishlist.getId(), productSlug)) {
            return buildResponse(wishlist);
        }

        Product product = productRepository
                .findBySlugAndLanguage(
                        productSlug,
                        LocaleContextHolder.getLocale().getLanguage())
                .orElseThrow(ProductNotFoundException::new);

        wishlistItemRepository.save(WishlistItem.builder()
                .wishlist(wishlist)
                .product(product)
                .build());

        return buildResponse(getOrCreateWishlist(principal.getUserId()));
    }

    @Transactional
    public WishlistResponse removeItem(String productSlug, UserPrincipal principal) {
        Wishlist wishlist = getOrCreateWishlist(principal.getUserId());

        wishlistItemRepository
                .findByWishlistIdAndProductSlug(wishlist.getId(), productSlug)
                .ifPresent(wishlistItemRepository::delete);

        return buildResponse(getOrCreateWishlist(principal.getUserId()));
    }

    private Wishlist getOrCreateWishlist(Long userId) {
        return wishlistRepository.findByUserId(userId)
                .orElseGet(() -> wishlistRepository.save(
                        Wishlist.builder()
                                .user(User.builder().id(userId).build())
                                .build()));
    }

    private WishlistResponse buildResponse(Wishlist wishlist) {
        String language = LocaleContextHolder.getLocale().getLanguage();

        List<WishlistItemResponse> items = wishlist.getItems().stream()
                .map(item -> {
                    Product product = item.getProduct();

                    String name = productTranslationRepository
                            .findByProductIdAndLanguage(product.getId(), language)
                            .map(ProductTranslation::getName)
                            .orElse(product.getSlug());

                    String imageUrl = productMediaRepository
                            .findByProductIdAndIsPrimary(product.getId(), true)
                            .map(ProductMedia::getUrl)
                            .orElse(null);

                    BigDecimal lowestPrice = variantRepository
                            .findByProductId(product.getId()).stream()
                            .filter(ProductVariant::isActive)
                            .map(ProductVariant::getPrice)
                            .min(BigDecimal::compareTo)
                            .orElse(null);

                    return WishlistItemResponse.builder()
                            .id(item.getId())
                            .productSlug(product.getSlug())
                            .productName(name)
                            .primaryImageUrl(imageUrl)
                            .lowestPrice(lowestPrice)
                            .build();
                })
                .toList();

        return WishlistResponse.builder()
                .id(wishlist.getId())
                .items(items)
                .itemCount(items.size())
                .build();
    }
}