package com.nocteon.nocteon_api.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.order.entity.ReturnItem;

public interface ReturnItemRepository extends JpaRepository<ReturnItem, Long> {

    /**
     * How many units of one order line already came back.
     *
     * <p>REJECTED returns are excluded: a refused return brought nothing back,
     * so its units are still returnable. This is the number the create guard
     * compares the new quantity against.
     */
    @Query("""
            select coalesce(sum(i.quantity), 0)
            from ReturnItem i
            join i.orderReturn r
            where i.orderItem.id = :orderItemId
              and r.status <> com.nocteon.nocteon_api.order.enums.ReturnStatus.REJECTED
            """)
    int returnedQuantityFor(@Param("orderItemId") Long orderItemId);
}
