package com.nocteon.nocteon_api.order.event;

import com.nocteon.nocteon_api.order.enums.OrderStatus;

/**
 * The order's status changed, and the customer is to be told.
 *
 * <p>Published only when the change was made with "notify the customer"
 * ticked: the admin decides who hears about it.
 *
 * @param orderId    the order
 * @param fromStatus where it came from (null for the first step)
 * @param toStatus   where it went
 * @param note       why, when the admin said
 */
public record OrderStatusChangedEvent(
                Long orderId,
                OrderStatus fromStatus,
                OrderStatus toStatus,
                String note) {
}
