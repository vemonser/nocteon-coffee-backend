package com.nocteon.nocteon_api.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.order.entity.OrderNote;

/**
 * The internal notes of the orders.
 *
 * <p>Newest first: the page shows the latest note at the top, the way a
 * conversation reads.
 */
public interface OrderNoteRepository extends JpaRepository<OrderNote, Long> {

        List<OrderNote> findByOrderIdOrderByCreatedAtDesc(Long orderId);

        long countByOrderId(Long orderId);
}
