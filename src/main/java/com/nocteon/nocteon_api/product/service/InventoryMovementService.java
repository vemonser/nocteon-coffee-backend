package com.nocteon.nocteon_api.product.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.audit.service.AuditLogService;
import com.nocteon.nocteon_api.common.exception.notFound.ProductVariantNotFoundException;
import com.nocteon.nocteon_api.common.security.ActorResolver;
import com.nocteon.nocteon_api.common.security.AuditActor;
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
    private final AuditLogService auditLogService;

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
        // The audit row and the movement describe ONE change: same before/after,
        // same reason, same transaction. The movement is the per-variant ledger;
        // the audit row is the "who did this on purpose" the audit page searches.
        auditManualAdjustment(variant, before, after, reason.trim());
        return new StockChange(variantId, before, after, delta);
    }

    /**
     * Put units back on the shelf because a return was approved (FE-20).
     *
     * <p>The only path that ADDS stock for a reason the store did not type: the
     * quantity is what the customer actually sent back. It is written through
     * this one writer like every other change, so the FE-13 log stays the single
     * record of what happened to a stock number — reason {@code RETURN}, source
     * {@code RETURN} and {@code source_id} = the return, which is the row a
     * reader can open to see why.
     *
     * @param variantId the variant the units go back to
     * @param quantity  units returned; refused when it is not positive
     * @param note      the return's own reason — the sentence in the log
     * @param actor     the admin who approved it
     * @param returnId  {@code returns.id}, the source of the row
     */
    @Transactional
    public StockChange restockForReturn(Long variantId, int quantity, String note, String actor, Long returnId) {
        if (quantity <= 0) {
            // A return line is already > 0 by construction (request validation
            // and the table's CHECK). Reaching here means a caller built the
            // line by hand, and a zero row would explain nothing.
            throw new InvalidStockAdjustmentException("error.inventory.quantityNotPositive");
        }
        variantRepository.findById(variantId)
                .orElseThrow(ProductVariantNotFoundException::new);

        int before = variantRepository.findStockById(variantId);
        variantRepository.incrementStock(variantId, quantity);
        int after = variantRepository.findStockById(variantId);

        record(variantId, before, after, InventoryMovementReason.RETURN,
                note == null ? null : note.trim(), actor, InventoryMovementSource.RETURN, returnId);
        return new StockChange(variantId, before, after, after - before);
    }

    /**
     * Put units back on the shelf because an order that had already consumed
     * them was cancelled.
     *
     * <p>The one path that ADDS stock because an ORDER died, and the mirror of
     * {@link #consumeForOrder}: the quantity is what the order took, and
     * {@code source_id} is the order that explains it. Reason
     * {@code ORDER_CANCELLED}, source {@code ORDER} — the same source as the
     * consumption, so both halves of the story sit behind one id.
     *
     * <p>Who decides that a restock is owed is the caller, and it decides from
     * {@link #consumedByOrder} — the ledger below — not from the order's
     * payment method. That is what keeps a cancellation from inventing units
     * for an order that never took any.
     *
     * @param variantId the variant the units go back to
     * @param quantity  units returned; refused when it is not positive
     * @param note      the cancellation reason — the sentence in the log
     * @param actor     whoever cancelled (admin e-mail, or system)
     * @param orderId   {@code orders.id}, the source of the row
     */
    @Transactional
    public StockChange restockForCancelledOrder(Long variantId, int quantity, String note, String actor, Long orderId) {
        if (quantity <= 0) {
            // The quantity comes from a consumption row, so it is > 0 by
            // construction; a zero or negative one would explain nothing.
            throw new InvalidStockAdjustmentException("error.inventory.quantityNotPositive");
        }
        variantRepository.findById(variantId)
                .orElseThrow(ProductVariantNotFoundException::new);

        int before = variantRepository.findStockById(variantId);
        variantRepository.incrementStock(variantId, quantity);
        int after = variantRepository.findStockById(variantId);

        record(variantId, before, after, InventoryMovementReason.ORDER_CANCELLED,
                note == null ? null : note.trim(), actor, InventoryMovementSource.ORDER, orderId);
        return new StockChange(variantId, before, after, after - before);
    }

    /**
     * What this order ACTUALLY took off the shelf: variant id → units, summed
     * over every consumption the order wrote.
     *
     * <p>Read from the ledger rather than derived from the order's status or
     * payment method, because those say what the order is, not what happened to
     * the stock. A checkout whose {@link #consumeForOrder} found nothing writes
     * no row and therefore appears here with nothing — so cancelling it can
     * never conjure units that were never taken. An empty answer is the normal
     * case for an unpaid order: its units are only RESERVED, which is not a
     * stock change and has never written a row.
     *
     * <p>Ordered by variant id, so a cancellation restocks in a stable order.
     */
    @Transactional(readOnly = true)
    public Map<Long, Integer> consumedByOrder(Long orderId) {
        if (orderId == null) {
            return Map.of();
        }
        Map<Long, Integer> consumed = new LinkedHashMap<>();
        for (Object[] row : movementRepository.sumConsumedByOrder(
                InventoryMovementSource.ORDER, orderId, InventoryMovementReason.ORDER)) {
            // The rows are negative (units left the shelf); the caller needs the
            // units to put BACK, so the sign is flipped here, once.
            long delta = ((Number) row[1]).longValue();
            consumed.put((Long) row[0], Math.toIntExact(-delta));
        }
        return consumed;
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

    /**
     * The audit row for a hand-made stock change.
     *
     * <p>Separate from the movement on purpose: the movement is the variant's
     * ledger (it also records orders and returns, with no human behind them),
     * while this row is the deliberate act — an admin typed a reason and changed
     * a number the store sells on. The actor comes from the security context,
     * because the row must carry the id AND the e-mail; the movement keeps the
     * display label it is given.
     */
    private void auditManualAdjustment(ProductVariant variant, int before, int after, String reason) {
        Map<String, Object> oldValues = new LinkedHashMap<>();
        oldValues.put("stockQuantity", before);

        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("stockQuantity", after);
        newValues.put("delta", after - before);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("reason", reason);
        metadata.put("reasonCode", InventoryMovementReason.MANUAL_ADJUSTMENT.name());

        AuditActor actor = ActorResolver.currentAuditActor();
        auditLogService.logSuccess(AuditAction.PRODUCT_STOCK_ADJUST, actor.userId(), actor.userEmail(),
                "ProductVariant", variant.getId(), variant.getSku(),
                null, null, oldValues, newValues, metadata);
    }

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
