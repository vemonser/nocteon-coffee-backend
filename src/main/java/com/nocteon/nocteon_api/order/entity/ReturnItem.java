package com.nocteon.nocteon_api.order.entity;

import com.nocteon.nocteon_api.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * One line of a return: how many units of one order line came back.
 *
 * <p>The line points at the ORDER ITEM, not at the variant: the question a
 * warehouse asks is "how many of the 3 bags on this order came back", and the
 * answer has to stay attached to the price that was charged. The variant is
 * reached through the item.
 *
 * <p>{@code quantity} is what was returned; it is capped by what is left of the
 * order line (the ordered quantity minus what earlier returns already brought
 * back), and the cap is enforced by the service because the sum has to be read
 * from the other returns.
 */
@Entity
@SuperBuilder
@Table(name = "return_items")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ReturnItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_id", nullable = false)
    private OrderReturn orderReturn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    /** Units returned. Always > 0: the table's CHECK says so too. */
    @Column(name = "quantity", nullable = false)
    private int quantity;

    /** A per-line remark — "bag torn", "wrong grind" — when the reason is not enough. */
    @Column(name = "note", length = 500)
    private String note;
}
