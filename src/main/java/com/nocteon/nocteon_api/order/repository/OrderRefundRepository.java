package com.nocteon.nocteon_api.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.order.entity.OrderRefund;

/**
 * The refunds of the orders.
 *
 * <p>Oldest first: the net paid is the total minus the SUM, and a reader
 * reads the refunds in the order they happened.
 */
public interface OrderRefundRepository extends JpaRepository<OrderRefund, Long> {

        List<OrderRefund> findByOrderIdOrderByCreatedAtAsc(Long orderId);
}
