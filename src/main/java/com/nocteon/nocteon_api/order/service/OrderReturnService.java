package com.nocteon.nocteon_api.order.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.common.exception.notFound.OrderNotFoundException;
import com.nocteon.nocteon_api.order.dto.request.CreateReturnRequest;
import com.nocteon.nocteon_api.order.dto.response.OrderReturnResponse;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.entity.OrderItem;
import com.nocteon.nocteon_api.order.entity.OrderRefund;
import com.nocteon.nocteon_api.order.entity.OrderReturn;
import com.nocteon.nocteon_api.order.entity.OrderStatusHistory;
import com.nocteon.nocteon_api.order.entity.ReturnItem;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.order.enums.ReturnStatus;
import com.nocteon.nocteon_api.order.exception.InvalidReturnException;
import com.nocteon.nocteon_api.order.exception.ReturnAlreadyDecidedException;
import com.nocteon.nocteon_api.order.exception.ReturnNotFoundException;
import com.nocteon.nocteon_api.order.repository.OrderRefundRepository;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.order.repository.OrderReturnRepository;
import com.nocteon.nocteon_api.order.repository.OrderStatusHistoryRepository;
import com.nocteon.nocteon_api.order.repository.ReturnItemRepository;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.service.InventoryMovementService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Returns / RMA (FE-20).
 *
 * <p>Three rules, and the second is the one this class exists for:
 *
 * <ol>
 *   <li>A return says WHAT came back — an order line, a quantity, a reason —
 *       and the quantity can never exceed what is still returnable on that
 *       line (ordered minus what earlier, non-rejected returns brought back).</li>
 *   <li>APPROVING puts the units back through
 *       {@link InventoryMovementService#restockForReturn}. That is the only
 *       place stock changes, so the FE-13 movement log stays the single record
 *       of it — reason {@code RETURN}, source {@code RETURN}, source id = the
 *       return. Rejecting moves nothing.</li>
 *   <li>A decision is recorded ONCE. Approving an already approved return is a
 *       retry, not a second restock: the row is read FOR UPDATE, the status is
 *       checked, and an APPROVED row is returned unchanged. Approving a
 *       rejected return (or the reverse) is a 409 — history is not rewritten.</li>
 * </ol>
 *
 * <p>The return also writes the order's TIMELINE: one {@code order_status_history}
 * row per event with {@code event_type} set (RETURN_REQUESTED, RETURN_APPROVED,
 * RETURN_REJECTED), in the same transaction as the change it describes. There is
 * no second timeline.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderReturnService {

    /** The event names the timeline carries. Also the value in {@code event_type}. */
    public static final String EVENT_REQUESTED = "RETURN_REQUESTED";
    public static final String EVENT_APPROVED = "RETURN_APPROVED";
    public static final String EVENT_REJECTED = "RETURN_REJECTED";

    private final OrderRepository orderRepository;
    private final OrderReturnRepository returnRepository;
    private final ReturnItemRepository returnItemRepository;
    private final OrderRefundRepository refundRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final InventoryMovementService inventoryService;

    /**
     * Record a return.
     *
     * @param orderId the order the goods belong to
     * @param request the lines, the quantities and the reason
     * @param actor   the admin recording it
     */
    @Transactional
    public OrderReturnResponse create(Long orderId, CreateReturnRequest request, String actor) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(OrderNotFoundException::new);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            // Nothing was delivered, so nothing can come back: a return on a
            // cancelled order would restock units the store still has.
            throw new InvalidReturnException("error.return.orderCancelled");
        }

        Map<Long, OrderItem> orderItems = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            orderItems.put(item.getId(), item);
        }
        if (orderItems.isEmpty()) {
            throw new InvalidReturnException("error.return.noItems");
        }

        String reason = request.getReason() == null ? "" : request.getReason().trim();
        if (reason.isEmpty()) {
            // @NotBlank already refuses it; this is the belt for a caller that
            // reaches the service directly.
            throw new InvalidReturnException("error.return.reasonRequired");
        }

        OrderRefund refund = resolveRefund(orderId, request.getRefundId());

        OrderReturn orderReturn = OrderReturn.builder()
                .order(order)
                .status(ReturnStatus.REQUESTED)
                .reason(reason)
                .requestedBy(actor)
                .refund(refund)
                .build();

        Set<Long> seen = new HashSet<>();
        for (CreateReturnRequest.Item line : request.getItems()) {
            if (!seen.add(line.getOrderItemId())) {
                throw new InvalidReturnException("error.return.duplicateItem");
            }
            OrderItem orderItem = orderItems.get(line.getOrderItemId());
            if (orderItem == null) {
                // The line is not on THIS order. Same answer as "no such line":
                // the caller has no business learning about other orders' rows.
                throw new InvalidReturnException("error.return.itemNotOnOrder");
            }

            int alreadyReturned = returnItemRepository.returnedQuantityFor(orderItem.getId());
            int available = orderItem.getQuantity() - alreadyReturned;
            int requested = line.getQuantity() == null ? 0 : line.getQuantity();
            if (requested < 1) {
                throw new InvalidReturnException("error.return.quantityRequired");
            }
            if (requested > available) {
                // Says HOW MANY are left: "too many" alone leaves the admin
                // guessing, and the number is what the form should cap at.
                throw new InvalidReturnException("error.return.quantityTooLarge", String.valueOf(available));
            }

            orderReturn.getItems().add(ReturnItem.builder()
                    .orderReturn(orderReturn)
                    .orderItem(orderItem)
                    .quantity(requested)
                    .note(blankToNull(line.getNote()))
                    .build());
        }

        orderReturn = returnRepository.save(orderReturn);

        recordEvent(order, EVENT_REQUESTED, summaryOf(orderReturn), actor);

        log.info("Return {} recorded on order {} by {} ({} line(s))",
                orderReturn.getId(), orderId, actor, orderReturn.getItems().size());

        return toResponse(orderReturn);
    }

    /** The returns of one order, newest first. */
    @Transactional(readOnly = true)
    public List<OrderReturnResponse> getReturns(Long orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new OrderNotFoundException();
        }
        return returnRepository.findByOrderIdOrderByCreatedAtDescIdDesc(orderId).stream()
                .map(OrderReturnService::toResponse)
                .toList();
    }

    /**
     * Decide a return: approve (restock) or reject (restock nothing).
     *
     * <p>Repeating the SAME decision is safe and answers the row unchanged.
     * The opposite decision is a 409.
     *
     * @param orderId  the order in the URL — the return must belong to it
     * @param returnId the return being decided
     * @param decision {@link ReturnStatus#APPROVED} or {@link ReturnStatus#REJECTED}
     * @param actor    the admin deciding
     */
    @Transactional
    public OrderReturnResponse decide(Long orderId, Long returnId, ReturnStatus decision, String actor) {
        if (decision != ReturnStatus.APPROVED && decision != ReturnStatus.REJECTED) {
            throw new InvalidReturnException("error.return.unknownDecision");
        }
        if (!orderRepository.existsById(orderId)) {
            throw new OrderNotFoundException();
        }

        // FOR UPDATE: a second concurrent approval waits here, then reads the
        // status the first one committed and returns without restocking.
        OrderReturn orderReturn = returnRepository.findByIdForUpdate(returnId)
                .filter(row -> row.getOrder() != null && orderId.equals(row.getOrder().getId()))
                .orElseThrow(ReturnNotFoundException::new);

        if (orderReturn.getStatus() == decision) {
            // The retry: no restock, no second timeline row, no error. A client
            // whose response was lost must be able to ask again.
            return toResponse(orderReturn);
        }
        if (!orderReturn.isRequested()) {
            throw new ReturnAlreadyDecidedException();
        }

        Instant now = Instant.now();
        orderReturn.setStatus(decision);
        orderReturn.setDecidedBy(actor);
        orderReturn.setDecidedAt(now);

        if (decision == ReturnStatus.APPROVED) {
            for (ReturnItem line : orderReturn.getItems()) {
                inventoryService.restockForReturn(
                        line.getOrderItem().getProductVariant().getId(),
                        line.getQuantity(),
                        orderReturn.getReason(),
                        actor,
                        orderReturn.getId());
            }
            recordEvent(orderReturn.getOrder(), EVENT_APPROVED, summaryOf(orderReturn), actor);
        } else {
            recordEvent(orderReturn.getOrder(), EVENT_REJECTED, summaryOf(orderReturn), actor);
        }

        orderReturn = returnRepository.save(orderReturn);

        log.info("Return {} on order {} {} by {}",
                orderReturn.getId(), orderId, decision, actor);

        return toResponse(orderReturn);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    /** The refund must exist and belong to the same order. */
    private OrderRefund resolveRefund(Long orderId, Long refundId) {
        if (refundId == null) {
            return null;
        }
        return refundRepository.findById(refundId)
                .filter(refund -> refund.getOrder() != null && orderId.equals(refund.getOrder().getId()))
                .orElseThrow(() -> new InvalidReturnException("error.return.refundNotOnOrder"));
    }

    /**
     * The timeline row, written in the SAME transaction as the change.
     *
     * <p>One table (order_status_history), one mechanism: the order page already
     * merges these rows into its activity log, and a return has no business
     * starting a second timeline.
     */
    private void recordEvent(Order order, String eventType, String note, String actor) {
        historyRepository.save(new OrderStatusHistory(order, order.getStatus(), eventType, note, actor));
    }

    /**
     * "2 × Ethiopia Yirgacheffe, 1 × Kenya Nyeri" — the sentence the timeline
     * shows under the event.
     *
     * <p>Built from the lines, so the timeline says WHAT came back without a
     * second request. The product name is the catalog's English name, the same
     * one the order's own line carries.
     */
    private static String summaryOf(OrderReturn orderReturn) {
        List<String> parts = new ArrayList<>();
        for (ReturnItem line : orderReturn.getItems()) {
            String name = productName(line.getOrderItem());
            parts.add(line.getQuantity() + " × " + (name != null ? name : line.getOrderItem().getProductVariant().getSku()));
        }
        return String.join(", ", parts);
    }

    private static String productName(OrderItem orderItem) {
        if (orderItem.getProductVariant() == null || orderItem.getProductVariant().getProduct() == null) {
            return null;
        }
        Product product = orderItem.getProductVariant().getProduct();
        return product.getTranslations().stream()
                .filter(t -> "en".equals(t.getLanguage()))
                .map(ProductTranslation::getName)
                .findFirst()
                .orElseGet(() -> product.getTranslations().isEmpty() ? null
                        : product.getTranslations().iterator().next().getName());
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static OrderReturnResponse toResponse(OrderReturn orderReturn) {
        List<OrderReturnResponse.Item> items = orderReturn.getItems().stream()
                .map(line -> {
                    OrderItem orderItem = line.getOrderItem();
                    return OrderReturnResponse.Item.builder()
                            .id(line.getId())
                            .orderItemId(orderItem != null ? orderItem.getId() : null)
                            .variantId(orderItem != null && orderItem.getProductVariant() != null
                                    ? orderItem.getProductVariant().getId()
                                    : null)
                            .sku(orderItem != null && orderItem.getProductVariant() != null
                                    ? orderItem.getProductVariant().getSku()
                                    : null)
                            .productName(orderItem != null ? productName(orderItem) : null)
                            .quantity(line.getQuantity())
                            .note(line.getNote())
                            .build();
                })
                .toList();

        return OrderReturnResponse.builder()
                .id(orderReturn.getId())
                .orderId(orderReturn.getOrder() != null ? orderReturn.getOrder().getId() : null)
                .status(orderReturn.getStatus() != null ? orderReturn.getStatus().name() : null)
                .reason(orderReturn.getReason())
                .requestedBy(orderReturn.getRequestedBy())
                .decidedBy(orderReturn.getDecidedBy())
                .decidedAt(orderReturn.getDecidedAt())
                .refundId(orderReturn.getRefund() != null ? orderReturn.getRefund().getId() : null)
                .createdAt(orderReturn.getCreatedAt())
                .updatedAt(orderReturn.getUpdatedAt())
                .items(items)
                .build();
    }
}
