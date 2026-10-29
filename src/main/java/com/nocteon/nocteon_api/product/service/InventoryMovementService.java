package com.nocteon.nocteon_api.product.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.common.exception.notFound.ProductVariantNotFoundException;
import com.nocteon.nocteon_api.common.exception.product.InvalidStockAdjustmentException;
import com.nocteon.nocteon_api.common.exception.product.StockAdjustmentReasonRequiredException;
import com.nocteon.nocteon_api.product.entity.InventoryMovement;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.enums.InventoryMovementReason;
import com.nocteon.nocteon_api.product.enums.InventoryMovementSource;
import com.nocteon.nocteon_api.product.repository.InventoryMovementRepository;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The ONE place a stock number changes.
 *
 * <p>{@code product_variants.stock_quantity} used to be mutated in four
 * unrelated spots — the checkout (COD consumption), the payment webhook, the
 * product form and nothing else, because there was no way to adjust stock on
 * purpose. Each of them read the count before, ran its own {@code UPDATE}, and
 * read the count after; none of them wrote down what it had done.
 *
 * <p>Every path now goes through this class, and every path writes an
 * {@link InventoryMovement} in the SAME transaction as the mutation. That is the
 * rule that makes the log trustworthy: a rolled-back order, a refused payment or
 * a failed product save leaves no row, because there is nothing to explain.
 *
 * <p>What is NOT a movement: {@code reserved_quantity}. Reserving units for a
 * checkout does not change what is on the shelf — it changes what is promised —
 * and logging it would fill the history with rows whose before and after are
 * equal.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryMovementService {

    /** The most movements one read may ask for. */
    public static final int MAX_HISTORY = 50;

    private final ProductVariantRepository variantRepository;
    private final InventoryMovementRepository movementRepository;

    /** The pair of numbers a caller may need (the low-stock notification does). */
    public record StockChange(Long variantId, int before, int after, int delta) {
        /** Did this change cross the variant's reorder threshold downwards? */
        public boolean crossedReorderThreshold(int reorderThreshold) {
            return before > reorderThreshold && after <= reorderThreshold;
        }
    }

    /**
     * Take units off the shelf for an order.
     *
     * <p>The conditional UPDATE is the concurrency guard: only one caller can
     * consume the same units. When it changes nothing, the stock was already
     * gone — the caller decides what that means for its flow, so no movement row
     * is written and {@code null} comes back.
     *
     * @param actor who caused it: the customer's e-mail, or a system worker
     */
    @Transactional
    public StockChange consumeForOrder(Long variantId, int quantity, String actor, Long orderId) {
        int before = variantRepository.findStockById(variantId);
        int updated = variantRepository.consumeStock(variantId, quantity);
        if (updated == 0) {
            log.warn("Stock for variant {} was already below {} when order {} tried to consume it",
                    variantId, quantity, orderId);
            return null;
        }
        int after = variantRepository.findStockById(variantId);
        record(variantId, before, after, InventoryMovementReason.ORDER, null, actor,
                InventoryMovementSource.ORDER, orderId);
        return new StockChange(variantId, before, after, after - before);
    }

    /**
     * Change the number by hand, with the reason the admin typed.
     *
     * @param delta  units added (positive) or removed (negative); zero is refused
     * @param reason the admin's sentence — required, and trimmed
     * @param actor  the admin's e-mail
     */
    @Transactional
    public StockChange adjustManually(Long variantId, int delta, String reason, String actor) {
        if (reason == null || reason.isBlank()) {
            throw new StockAdjustmentReasonRequiredException();
        }
        if (delta == 0) {
            throw new InvalidStockAdjustmentException("error.inventory.deltaZero");
        }

        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(ProductVariantNotFoundException::new);

        int before = variant.getStockQuantity();
        int after = before + delta;
        if (after < 0) {
            throw new InvalidStockAdjustmentException("error.inventory.belowZero");
        }

        // Written through the entity so the @Version column still guards two
        // admins editing the same variant at once (an optimistic-lock failure
        // rolls the movement row back with it).
        variant.setStockQuantity(after);
        variantRepository.saveAndFlush(variant);

        record(variantId, before, after, InventoryMovementReason.MANUAL_ADJUSTMENT,
                reason.trim(), actor, InventoryMovementSource.MANUAL, null);
        return new StockChange(variantId, before, after, delta);
    }

    /**
     * The product form was saved with a different stock figure on an EXISTING
     * variant.
     *
     * <p>A field-by-field comparison, because that is what the form does: it
     * sends the whole variant list back, and a save that changed only the price
     * must not produce a movement. A brand-new variant is skipped: its stock has
     * no "before", and "0 was created with 10" is not a change anybody made.
     */
    @Transactional
    public void recordProductEdit(Long variantId, Long productId, int before, int after, String actor) {
        if (before == after) {
            return;
        }
        record(variantId, before, after, InventoryMovementReason.PRODUCT_EDIT, null, actor,
                InventoryMovementSource.PRODUCT, productId);
    }

    /** The last {@code limit} movements of a variant, newest first. */
    @Transactional(readOnly = true)
    public List<InventoryMovement> recent(Long variantId, int limit) {
        int size = Math.max(1, Math.min(limit, MAX_HISTORY));
        // A retired variant keeps its history: the panel of a trashed product
        // still explains where its stock went. The existence check therefore
        // looks at the raw row, not at the soft-delete-filtered view.
        return movementRepository.findByVariantIdOrderByCreatedAtDescIdDesc(
                variantId, PageRequest.of(0, size));
    }

    // ─── the one write ───────────────────────────────────────────────────────

    private void record(Long variantId, int before, int after, InventoryMovementReason reason,
            String reasonNote, String actor, InventoryMovementSource source, Long sourceId) {
        movementRepository.save(InventoryMovement.builder()
                .variantId(variantId)
                .stockBefore(before)
                .stockAfter(after)
                .delta(after - before)
                .reason(reason)
                .reasonNote(reasonNote)
                .actor(actor == null || actor.isBlank() ? "system" : actor)
                .sourceType(source)
                .sourceId(sourceId)
                .build());
    }
}
