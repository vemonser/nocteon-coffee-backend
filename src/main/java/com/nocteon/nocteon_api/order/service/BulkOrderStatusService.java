package com.nocteon.nocteon_api.order.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.nocteon.nocteon_api.common.exception.BaseApiException;
import com.nocteon.nocteon_api.order.dto.request.BulkUpdateOrderStatusRequest;
import com.nocteon.nocteon_api.order.dto.request.UpdateOrderStatusRequest;
import com.nocteon.nocteon_api.order.dto.response.BulkStatusResultResponse;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.repository.OrderRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * ONE request, N status changes, N transactions (FE-18b).
 *
 * <p>Before this class the dashboard changed a selection by sending one
 * request per order, one at a time. The endpoint existed for a single order
 * only, so twelve rows were twelve round trips. This service takes the batch
 * and keeps the property the sequential run had: a row that cannot move does
 * not take the rows that can down with it.
 *
 * <p>Three rules, and the second is the reason the class exists:
 *
 * <ol>
 *   <li>The STATE MACHINE is not re-implemented here. Every row goes through
 *       {@link OrderStatusService#updateStatus(Long, UpdateOrderStatusRequest, String)},
 *       the same method the single endpoint uses, so the transition map, the
 *       cancellation reason, the stock release, the history row and the
 *       shipment timestamps are the single endpoint's — not a copy that can
 *       drift from it.</li>
 *   <li>EVERY ROW COMMITS ON ITS OWN: each call runs inside a
 *       {@code REQUIRES_NEW} transaction ({@link #perRow}). A row refused
 *       later therefore cannot roll back a row that already succeeded, and
 *       the caller never sees a half-applied batch without the per-row
 *       reasons that explain it.</li>
 *   <li>A REFUSAL IS A ROW, NOT A CALL: a refused or unknown order comes back
 *       as {@code success=false} with the server's own sentence, which is the
 *       same message the single endpoint would have put in its 404/409 body.
 *       The call itself is a 200.</li>
 * </ol>
 *
 * <p>{@code notifyCustomer} is never set: it belongs with a single order,
 * where the caller writes the note the mail quotes. The bulk path has one
 * shared note for the whole batch, so nothing is mailed from here — the admin
 * can still open an order and tell its customer.
 */
@Service
@Slf4j
public class BulkOrderStatusService {

        private final OrderStatusService orderStatusService;
        private final OrderRepository orderRepository;
        private final MessageSource messageSource;

        /**
         * The per-row boundary.
         *
         * <p>An explicit {@code REQUIRES_NEW} template rather than a
         * {@code @Transactional} method: the loop must NOT be one
         * transaction, and a self-invocation would bypass the Spring proxy in
         * silence.
         */
        private final TransactionTemplate perRow;

        public BulkOrderStatusService(
                        OrderStatusService orderStatusService,
                        OrderRepository orderRepository,
                        MessageSource messageSource,
                        PlatformTransactionManager transactionManager) {

                this.orderStatusService = orderStatusService;
                this.orderRepository = orderRepository;
                this.messageSource = messageSource;
                this.perRow = new TransactionTemplate(transactionManager);
                this.perRow.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }

        /**
         * Apply one status to a set of orders.
         *
         * @param request the ids (1..100), the target status and the shared note
         * @param actor   who did it: the admin's e-mail, or the system
         * @return one result per distinct id, in the order the ids were sent
         */
        public List<BulkStatusResultResponse> bulkUpdate(
                        BulkUpdateOrderStatusRequest request, String actor) {

                // Duplicates collapse: the caller may have sent the same row
                // twice (a stale selection), and a second attempt would refuse
                // a row that just moved. A null inside the list is not an id.
                List<Long> ids = request.getIds() == null
                                ? List.of()
                                : request.getIds().stream()
                                                .filter(java.util.Objects::nonNull)
                                                .collect(Collectors.collectingAndThen(
                                                                Collectors.toCollection(LinkedHashSet::new),
                                                                ArrayList::new));

                if (ids.isEmpty()) {
                        return List.of();
                }

                // The numbers are read once, up front, so a refusal can name
                // the order the warehouse knows. An order that is not there
                // (unknown, or soft-deleted) has no number to give.
                Map<Long, String> orderNumbers = orderRepository.findAllById(ids).stream()
                                .collect(Collectors.toMap(
                                                Order::getId,
                                                order -> order.getOrderNumber() == null
                                                                ? ""
                                                                : order.getOrderNumber(),
                                                (first, second) -> first));

                UpdateOrderStatusRequest rowRequest = new UpdateOrderStatusRequest();
                rowRequest.setStatus(request.getStatus());
                rowRequest.setNote(request.getNote() == null || request.getNote().isBlank()
                                ? null
                                : request.getNote());
                // Deliberately false: see the class comment.
                rowRequest.setNotifyCustomer(false);

                List<BulkStatusResultResponse> results = new ArrayList<>(ids.size());
                for (Long id : ids) {
                        results.add(updateOne(id, orderNumbers.get(id), rowRequest, actor));
                }

                long updated = results.stream().filter(BulkStatusResultResponse::isSuccess).count();
                log.info("Bulk status change to {} by {}: {} of {} orders updated",
                                request.getStatus(), actor, updated, results.size());

                return results;
        }

        /** One row: its own transaction, its own outcome, its own sentence. */
        private BulkStatusResultResponse updateOne(
                        Long id,
                        String orderNumber,
                        UpdateOrderStatusRequest rowRequest,
                        String actor) {

                try {
                        perRow.executeWithoutResult(
                                        status -> orderStatusService.updateStatus(id, rowRequest, actor));

                        return BulkStatusResultResponse.builder()
                                        .id(id)
                                        .orderNumber(orderNumber)
                                        .success(true)
                                        .message(message("order.bulk.rowUpdated", "Order status updated"))
                                        .build();

                } catch (BaseApiException ex) {
                        // The state machine (409), a missing order (404), a
                        // cancellation without a reason (400) — reported as
                        // this row's reason, never as the failure of the batch.
                        return BulkStatusResultResponse.builder()
                                        .id(id)
                                        .orderNumber(orderNumber)
                                        .success(false)
                                        .message(messageSource.getMessage(
                                                        ex.getMessageKey(),
                                                        ex.getMessageArgs(),
                                                        ex.getMessageKey(),
                                                        locale()))
                                        .build();

                } catch (RuntimeException ex) {
                        // Anything else is a real failure of THIS row (a lost
                        // optimistic-lock race, say). It is logged with the
                        // stack trace and reported without leaking internals.
                        log.warn("Bulk status change failed for order {}", id, ex);
                        return BulkStatusResultResponse.builder()
                                        .id(id)
                                        .orderNumber(orderNumber)
                                        .success(false)
                                        .message(message("error.unexpected",
                                                        "Something went wrong. Please try again."))
                                        .build();
                }
        }

        /** The current request's language — the caller's, not the server's. */
        private Locale locale() {
                return LocaleContextHolder.getLocale();
        }

        private String message(String key, String fallback) {
                return messageSource.getMessage(key, null, fallback, locale());
        }
}
