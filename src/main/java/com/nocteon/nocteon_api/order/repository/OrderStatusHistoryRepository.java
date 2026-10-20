package com.nocteon.nocteon_api.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.order.entity.OrderStatusHistory;

/**
 * The status history of the orders.
 *
 * <p>Always read oldest-first for one order: the page shows the chain, and a
 * chain read backwards is a list.
 */
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

        List<OrderStatusHistory> findByOrderIdOrderByCreatedAtAsc(Long orderId);

        List<OrderStatusHistory> findByOrderIdOrderByCreatedAtDesc(Long orderId);

        long countByOrderId(Long orderId);
}
