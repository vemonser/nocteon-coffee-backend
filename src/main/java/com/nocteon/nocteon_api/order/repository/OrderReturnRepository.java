package com.nocteon.nocteon_api.order.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.order.entity.OrderReturn;
import com.nocteon.nocteon_api.order.enums.ReturnStatus;

import jakarta.persistence.LockModeType;

/**
 * The returns of an order.
 *
 * <p>Always read newest-first for one order: the section of the order page
 * shows the latest return on top, and a reader deciding on it should not have
 * to scroll past last month's.
 */
public interface OrderReturnRepository extends JpaRepository<OrderReturn, Long> {

    List<OrderReturn> findByOrderIdOrderByCreatedAtDescIdDesc(Long orderId);

    /**
     * The decision reads the row FOR UPDATE.
     *
     * <p>That row lock is what makes "approve twice" safe: the second caller
     * waits for the first to commit, then reads APPROVED and returns the row
     * without restocking again. Without it, two concurrent approvals would both
     * see REQUESTED and both put the units back.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from OrderReturn r where r.id = :id")
    Optional<OrderReturn> findByIdForUpdate(@Param("id") Long id);

    long countByOrderIdAndStatus(Long orderId, ReturnStatus status);
}
