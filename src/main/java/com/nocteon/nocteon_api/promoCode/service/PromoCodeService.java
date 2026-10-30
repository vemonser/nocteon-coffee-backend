package com.nocteon.nocteon_api.promoCode.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.audit.service.DomainAuditRecorder;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.cart.entity.Cart;
import com.nocteon.nocteon_api.cart.entity.CartItem;
import com.nocteon.nocteon_api.cart.repository.CartRepository;
import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.category.repository.CategoryRepository;
import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.exception.product.CartEmptyException;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.promoCode.dto.request.PromoCodeCalculationResult;
import com.nocteon.nocteon_api.promoCode.dto.request.PromoCodeRequest;
import com.nocteon.nocteon_api.promoCode.dto.response.PromoCodeResponse;
import com.nocteon.nocteon_api.promoCode.entity.PromoCode;
import com.nocteon.nocteon_api.promoCode.entity.PromoCodeRedemption;
import com.nocteon.nocteon_api.promoCode.enums.PromoCodeDiscountType;
import com.nocteon.nocteon_api.promoCode.enums.PromoScopeType;
import com.nocteon.nocteon_api.promoCode.exceptions.PromoCodeAlreadyExistsException;
import com.nocteon.nocteon_api.promoCode.exceptions.PromoCodeAlreadyUsedException;
import com.nocteon.nocteon_api.promoCode.exceptions.PromoCodeCategoryRequiredException;
import com.nocteon.nocteon_api.promoCode.exceptions.PromoCodeExhaustedException;
import com.nocteon.nocteon_api.promoCode.exceptions.PromoCodeExpiredException;
import com.nocteon.nocteon_api.promoCode.exceptions.PromoCodeInactiveException;
import com.nocteon.nocteon_api.promoCode.exceptions.PromoCodeMinOrderNotMetException;
import com.nocteon.nocteon_api.promoCode.exceptions.PromoCodeNotApplicableException;
import com.nocteon.nocteon_api.promoCode.exceptions.PromoCodeNotFoundException;
import com.nocteon.nocteon_api.promoCode.repository.PromoCodeRedemptionRepository;
import com.nocteon.nocteon_api.promoCode.repository.PromoCodeRepository;
import com.nocteon.nocteon_api.common.repository.SearchPatterns;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromoCodeService {

    /** The entity_type every row for this catalogue records. */
    private static final String ENTITY = "PromoCode";

    private final PromoCodeRepository promoCodeRepository;
    private final PromoCodeRedemptionRepository redemptionRepository;
    private final CategoryRepository categoryRepository;
    private final CartRepository cartRepository;
    private final DomainAuditRecorder domainAudit;

    @Transactional(readOnly = true)
    public PageResponse<PromoCodeResponse> getAll(BaseFilterRequest filter) {
        Page<PromoCode> page = promoCodeRepository.findAll(filter.toPageable());
        return PageResponse.of(page.map(this::buildResponse));
    }

    @Transactional(readOnly = true)
    public PromoCodeResponse getById(Long id) {
        PromoCode promoCode = promoCodeRepository.findById(id)
                .orElseThrow(PromoCodeNotFoundException::new);
        return buildResponse(promoCode);
    }

    @Transactional(readOnly = true)
    public PromoCodeCalculationResult previewDiscount(String code, Long userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(CartEmptyException::new);

        if (cart.getItems().isEmpty()) {
            throw new CartEmptyException();
        }

        BigDecimal cartTotal = cart.getItems().stream()
                .map(item -> item.getProductVariant().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return calculateDiscountForCart(code, cart.getItems(), cartTotal, userId);
    }

    @Transactional
    public PromoCodeResponse create(PromoCodeRequest request) {
        if (promoCodeRepository.existsByCodeIgnoreCase(request.getCode())) {
            throw new PromoCodeAlreadyExistsException();
        }

        List<Category> categories = resolveCategories(request);

        PromoCode promoCode = PromoCode.builder()
                .code(request.getCode().toUpperCase())
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .minOrderAmount(request.getMinOrderAmount())
                .scopeType(request.getScopeType())
                // A mutable list: Hibernate merges a managed entity by clearing the
                // collection it is given, and List.of() cannot be cleared.
                .categories(new ArrayList<>(categories))
                .maxTotalRedemptions(request.getMaxTotalRedemptions())
                .maxRedemptionsPerUser(request.getMaxRedemptionsPerUser())
                .validFrom(request.getValidFrom())
                .validUntil(request.getValidUntil())
                .active(request.isActive())
                .build();

        promoCodeRepository.save(promoCode);

        domainAudit.created(AuditAction.PROMO_CODE_CREATE, ENTITY, promoCode.getId(), promoCode.getCode(),
                promoCodeFields(promoCode));

        return buildResponse(promoCode);
    }

    @Transactional
    public PromoCodeResponse update(Long id, PromoCodeRequest request) {
        PromoCode promoCode = promoCodeRepository.findById(id)
                .orElseThrow(PromoCodeNotFoundException::new);

        Map<String, Object> before = promoCodeFields(promoCode);

        boolean codeChanged = !promoCode.getCode().equalsIgnoreCase(request.getCode());
        if (codeChanged && promoCodeRepository.existsByCodeIgnoreCase(request.getCode())) {
            throw new PromoCodeAlreadyExistsException();
        }

        List<Category> categories = resolveCategories(request);

        promoCode.setCode(request.getCode().toUpperCase());
        promoCode.setDiscountType(request.getDiscountType());
        promoCode.setDiscountValue(request.getDiscountValue());
        promoCode.setMinOrderAmount(request.getMinOrderAmount());
        promoCode.setScopeType(request.getScopeType());
        // A mutable list: Hibernate merges a managed entity by clearing the
        // collection it is given, and List.of() cannot be cleared — which made
        // editing a GLOBAL code throw instead of saving.
        promoCode.setCategories(new ArrayList<>(categories));
        promoCode.setMaxTotalRedemptions(request.getMaxTotalRedemptions());
        promoCode.setMaxRedemptionsPerUser(request.getMaxRedemptionsPerUser());
        promoCode.setValidFrom(request.getValidFrom());
        promoCode.setValidUntil(request.getValidUntil());
        promoCode.setActive(request.isActive());

        promoCodeRepository.save(promoCode);

        domainAudit.updated(AuditAction.PROMO_CODE_UPDATE, ENTITY, promoCode.getId(), promoCode.getCode(),
                before, promoCodeFields(promoCode));

        return buildResponse(promoCode);
    }

    @Transactional
    public void delete(Long id) {
        PromoCode promoCode = promoCodeRepository.findById(id)
                .orElseThrow(PromoCodeNotFoundException::new);
        promoCode.softDelete();
        promoCodeRepository.save(promoCode);

        domainAudit.softDeleted(AuditAction.PROMO_CODE_DELETE, ENTITY, promoCode.getId(), promoCode.getCode(),
                promoCode.getDeletedAt());
    }

    /**
     * Permanently deletes a trashed promo code: the row itself (category links go with it through
     * the join table).
     *
     * <p>Trash-only: reads through {@code findDeletedById}, so a live or unknown id raises
     * {@link PromoCodeNotFoundException} instead of deleting anything. Soft-delete first — this
     * never touches live rows.
     */
    @Transactional
    public void permanentDelete(Long id) {
        PromoCode promoCode = promoCodeRepository.findDeletedById(id)
                .orElseThrow(PromoCodeNotFoundException::new);
        promoCodeRepository.delete(promoCode);
    }

    @Transactional(readOnly = true)
    public PageResponse<PromoCodeResponse> getAllDeleted(LookupFilterRequest filter) {
        String search = SearchPatterns.like(filter.getSearch());
        int pageNum = filter.getPage();
        int size = filter.getSize();
        String sort = filter.getSort();
        String direction = filter.getDirection();

        List<PromoCode> entities;
        if (search != null && !search.isEmpty()) {
            entities = promoCodeRepository.findDeletedBySearchNative(search);
        } else {
            entities = promoCodeRepository.findAllDeletedNative();
        }

        List<PromoCodeResponse> allResults = entities.stream()
                .map(this::buildResponse)
                .collect(Collectors.toList());

        int totalElements = allResults.size();

        if (sort != null) {
            allResults.sort((a, b) -> {
                int result = 0;
                switch (sort) {
                    case "code":
                        result = a.getCode().compareToIgnoreCase(b.getCode());
                        break;
                    case "createdAt":
                        result = a.getCreatedAt().compareTo(b.getCreatedAt());
                        break;
                    default:
                        result = 0;
                }
                return direction.equalsIgnoreCase("desc") ? -result : result;
            });
        }

        int fromIndex = pageNum * size;
        int toIndex = Math.min(fromIndex + size, allResults.size());
        if (fromIndex > allResults.size()) {
            fromIndex = allResults.size();
        }
        List<PromoCodeResponse> pagedResults = allResults.subList(fromIndex, toIndex);

        Page<PromoCodeResponse> pageObj = new PageImpl<>(pagedResults, PageRequest.of(pageNum, size), allResults.size());
        return PageResponse.of(pageObj);
    }

    @Transactional
    public void restore(Long id) {
        PromoCode promoCode = promoCodeRepository.findDeletedById(id)
                .orElseThrow(PromoCodeNotFoundException::new);
        promoCode.setDeletedAt(null);
        promoCodeRepository.save(promoCode);
        log.info("PromoCode restored: {}", promoCode.getCode());
    }

    private List<Category> resolveCategories(PromoCodeRequest request) {
        if (request.getScopeType() == PromoScopeType.GLOBAL) {
            return List.of();
        }

        if (request.getCategorySlugs() == null || request.getCategorySlugs().isEmpty()) {
            throw new PromoCodeCategoryRequiredException();
        }

        return categoryRepository.findBySlugIn(request.getCategorySlugs());
    }

    @Transactional(readOnly = true)
    public PromoCode getEntityById(Long id) {
        return promoCodeRepository.findById(id)
                .orElseThrow(PromoCodeNotFoundException::new);
    }

    @Transactional
    public void recordRedemption(PromoCode promoCode, Order order, Long userId, BigDecimal discountAmount) {
        PromoCodeRedemption redemption = PromoCodeRedemption.builder()
                .promoCode(promoCode)
                .user(User.builder().id(userId).build())
                .order(order)
                .discountAmount(discountAmount)
                .build();

        redemptionRepository.save(redemption);
    }

    @Transactional(readOnly = true)
    public PromoCodeCalculationResult calculateDiscountForCart(
            String code, List<CartItem> cartItems, BigDecimal cartTotal, Long userId) {

        // Locked read: the only caller runs inside the checkout transaction, so
        // the usage-count check below and the later redemption are atomic.
        PromoCode promoCode = promoCodeRepository.findByCodeIgnoreCaseForUpdate(code)
                .orElseThrow(PromoCodeNotFoundException::new);

        List<CartItem> eligibleItems = getEligibleCartItems(promoCode, cartItems);

        if (eligibleItems.isEmpty()) {
            throw new PromoCodeNotApplicableException();
        }

        BigDecimal eligibleAmount = eligibleItems.stream()
                .map(item -> item.getProductVariant().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        validatePromoCode(promoCode, cartTotal, eligibleAmount, userId);

        BigDecimal discountAmount = switch (promoCode.getDiscountType()) {
            case PERCENTAGE -> eligibleAmount
                    .multiply(promoCode.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case FIXED_AMOUNT -> promoCode.getDiscountValue().min(eligibleAmount);
            case FREE_SHIPPING -> BigDecimal.ZERO;
        };

        boolean freeShipping = promoCode.getDiscountType() == PromoCodeDiscountType.FREE_SHIPPING;

        return PromoCodeCalculationResult.builder()
                .promoCodeId(promoCode.getId())
                .discountAmount(discountAmount)
                .freeShipping(freeShipping)
                .build();
    }

    private List<CartItem> getEligibleCartItems(PromoCode promoCode, List<CartItem> cartItems) {
        if (promoCode.getScopeType() == PromoScopeType.GLOBAL) {
            return cartItems;
        }

        Set<Long> allowedCategoryIds = promoCode.getCategories().stream()
                .map(Category::getId)
                .collect(Collectors.toSet());

        return cartItems.stream()
                .filter(item -> allowedCategoryIds.contains(
                        item.getProductVariant().getProduct().getCategory().getId()))
                .toList();
    }

    private void validatePromoCode(PromoCode promoCode, BigDecimal cartTotal, BigDecimal eligibleAmount, Long userId) {
        if (!promoCode.isActive()) {
            throw new PromoCodeInactiveException();
        }

        Instant now = Instant.now();
        if (now.isBefore(promoCode.getValidFrom()) || now.isAfter(promoCode.getValidUntil())) {
            throw new PromoCodeExpiredException();
        }

        if (promoCode.getMinOrderAmount() != null) {
            BigDecimal amountToCheck = promoCode.getScopeType() == PromoScopeType.GLOBAL
                    ? cartTotal
                    : eligibleAmount;

            if (amountToCheck.compareTo(promoCode.getMinOrderAmount()) < 0) {
                throw new PromoCodeMinOrderNotMetException();
            }
        }

        if (promoCode.getMaxTotalRedemptions() != null) {
            long totalUsed = redemptionRepository.countByPromoCodeId(promoCode.getId());
            if (totalUsed >= promoCode.getMaxTotalRedemptions()) {
                throw new PromoCodeExhaustedException();
            }
        }

        long userUsageCount = redemptionRepository.countByPromoCodeIdAndUserId(promoCode.getId(), userId);
        if (promoCode.getMaxRedemptionsPerUser() != null
                && userUsageCount >= promoCode.getMaxRedemptionsPerUser()) {
            throw new PromoCodeAlreadyUsedException();
        }
    }

    /**
     * The change-visible state of one promo code, for an audit row.
     *
     * <p>The two validity dates are ISO strings, not {@code Instant}s: the audit
     * writer serialises with a plain ObjectMapper, which has no java.time support.
     * The category list is recorded by slug so a scope change reads as
     * "coffee-*-gear" rather than a list of numbers.
     */
    private static Map<String, Object> promoCodeFields(PromoCode promoCode) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("code", promoCode.getCode());
        fields.put("discountType",
                promoCode.getDiscountType() == null ? null : promoCode.getDiscountType().name());
        fields.put("discountValue", promoCode.getDiscountValue());
        fields.put("scopeType", promoCode.getScopeType() == null ? null : promoCode.getScopeType().name());
        fields.put("categories", promoCode.getCategories() == null ? List.of() : promoCode.getCategories().stream()
                .map(Category::getSlug)
                .sorted()
                .toList());
        fields.put("minOrderAmount", promoCode.getMinOrderAmount());
        fields.put("maxTotalRedemptions", promoCode.getMaxTotalRedemptions());
        fields.put("maxRedemptionsPerUser", promoCode.getMaxRedemptionsPerUser());
        fields.put("validFrom", promoCode.getValidFrom() == null ? null : promoCode.getValidFrom().toString());
        fields.put("validUntil", promoCode.getValidUntil() == null ? null : promoCode.getValidUntil().toString());
        fields.put("active", promoCode.isActive());
        return fields;
    }

    private PromoCodeResponse buildResponse(PromoCode promoCode) {
        long totalRedemptions = redemptionRepository.countByPromoCodeId(promoCode.getId());
        BigDecimal totalDiscountGiven = redemptionRepository.sumDiscountByPromoCodeId(promoCode.getId());

        BigDecimal usageRate = promoCode.getMaxTotalRedemptions() != null && promoCode.getMaxTotalRedemptions() > 0
                ? BigDecimal.valueOf(totalRedemptions)
                        .divide(BigDecimal.valueOf(promoCode.getMaxTotalRedemptions()), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP)
                : null;

        return PromoCodeResponse.builder()
                .id(promoCode.getId())
                .createdAt(promoCode.getCreatedAt())
                .code(promoCode.getCode())
                .discountType(promoCode.getDiscountType())
                .discountValue(promoCode.getDiscountValue())
                .minOrderAmount(promoCode.getMinOrderAmount())
                .scopeType(promoCode.getScopeType())
                .categorySlugs(promoCode.getCategories().stream().map(Category::getSlug).toList())
                .maxTotalRedemptions(promoCode.getMaxTotalRedemptions())
                .maxRedemptionsPerUser(promoCode.getMaxRedemptionsPerUser())
                .totalRedemptions(totalRedemptions)
                .totalDiscountGiven(totalDiscountGiven)
                .usageRate(usageRate)
                .validFrom(promoCode.getValidFrom())
                .validUntil(promoCode.getValidUntil())
                .active(promoCode.isActive())
                .build();
    }

}