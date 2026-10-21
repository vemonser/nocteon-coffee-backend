package com.nocteon.nocteon_api.order.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.common.exception.notFound.OrderNotFoundException;
import com.nocteon.nocteon_api.order.dto.request.CreateOrderNoteRequest;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.entity.OrderNote;
import com.nocteon.nocteon_api.order.repository.OrderNoteRepository;
import com.nocteon.nocteon_api.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

/**
 * The internal notes of an order.
 *
 * <p>A note is what the TEAM wrote about the order. It is never shown to the
 * customer, and it is never the customer's own note (that one is
 * {@code orders.notes}).
 */
@Service
@RequiredArgsConstructor
public class OrderNoteService {

        private final OrderNoteRepository noteRepository;
        private final OrderRepository orderRepository;

        /** Add a note. */
        @Transactional
        public OrderNote addNote(Long orderId, CreateOrderNoteRequest request, String author) {
                Order order = orderRepository.findById(orderId)
                                .orElseThrow(OrderNotFoundException::new);
                return noteRepository.save(new OrderNote(
                                order,
                                author,
                                request.getBody(),
                                request.isInternal()));
        }

        /** The notes of an order, newest first. */
        @Transactional(readOnly = true)
        public List<OrderNote> getNotes(Long orderId) {
                if (!orderRepository.existsById(orderId)) {
                        throw new OrderNotFoundException();
                }
                return noteRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        }
}
