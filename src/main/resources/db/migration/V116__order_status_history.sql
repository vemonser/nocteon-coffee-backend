-- ============================================================
-- V116: Populate order_status_history for seeded orders
-- ============================================================
-- Every seeded order gets a creation row (fromStatus=NULL, toStatus=PENDING)
-- plus transitions matching its current status.
-- changed_by = 'seed' for all.

-- 1. Creation row for all 17 orders
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT id, NULL, 'PENDING', NULL, 'seed', false, created_at
FROM orders
WHERE order_number IN (
    'ORD-2026-000001','ORD-2026-000002','ORD-2026-000003',
    'ORD-2026-000004','ORD-2026-000005','ORD-2026-000006',
    'ORD-2026-000007','ORD-2026-000008','ORD-2026-000009',
    'ORD-2026-000010','ORD-2026-000011','ORD-2026-000012',
    'ORD-2026-000013','ORD-2026-000014','ORD-2026-000015',
    'ORD-2026-000016','ORD-2026-000017'
)
AND NOT EXISTS (
    SELECT 1 FROM order_status_history h WHERE h.order_id = orders.id AND h.from_status IS NULL
);

-- 2. DELIVERED orders (1,2,3,4,5,9,11,16,17): PENDING -> CONFIRMED -> PROCESSING -> SHIPPED -> DELIVERED
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT id, 'PENDING', 'CONFIRMED', 'Payment received', 'seed', true, created_at + interval '1 hour'
FROM orders WHERE order_number IN (
    'ORD-2026-000001','ORD-2026-000002','ORD-2026-000003',
    'ORD-2026-000004','ORD-2026-000005','ORD-2026-000009',
    'ORD-2026-000011','ORD-2026-000016','ORD-2026-000017'
)
AND NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = orders.id AND h.from_status = 'PENDING' AND h.to_status = 'CONFIRMED');

INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT id, 'CONFIRMED', 'PROCESSING', 'Order confirmed', 'seed', false, created_at + interval '2 hours'
FROM orders WHERE order_number IN (
    'ORD-2026-000001','ORD-2026-000002','ORD-2026-000003',
    'ORD-2026-000004','ORD-2026-000005','ORD-2026-000009',
    'ORD-2026-000011','ORD-2026-000016','ORD-2026-000017'
)
AND NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = orders.id AND h.from_status = 'CONFIRMED' AND h.to_status = 'PROCESSING');

INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT id, 'PROCESSING', 'SHIPPED', 'Shipped to carrier', 'seed', true, created_at + interval '1 day'
FROM orders WHERE order_number IN (
    'ORD-2026-000001','ORD-2026-000002','ORD-2026-000003',
    'ORD-2026-000004','ORD-2026-000005','ORD-2026-000009',
    'ORD-2026-000011','ORD-2026-000016','ORD-2026-000017'
)
AND NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = orders.id AND h.from_status = 'PROCESSING' AND h.to_status = 'SHIPPED');

INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT id, 'SHIPPED', 'DELIVERED', 'Delivered to customer', 'seed', true, created_at + interval '3 days'
FROM orders WHERE order_number IN (
    'ORD-2026-000001','ORD-2026-000002','ORD-2026-000003',
    'ORD-2026-000004','ORD-2026-000005','ORD-2026-000009',
    'ORD-2026-000011','ORD-2026-000016','ORD-2026-000017'
)
AND NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = orders.id AND h.from_status = 'SHIPPED' AND h.to_status = 'DELIVERED');

-- 3. CANCELLED order 6 (COD): PENDING -> CANCELLED
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT id, 'PENDING', 'CANCELLED', 'Customer cancelled', 'seed', true, created_at + interval '30 minutes'
FROM orders WHERE order_number = 'ORD-2026-000006'
AND NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = orders.id AND h.from_status = 'PENDING' AND h.to_status = 'CANCELLED');

-- 4. CANCELLED order 7 (ONLINE): PENDING -> CONFIRMED -> CANCELLED
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT id, 'PENDING', 'CONFIRMED', 'Payment received', 'seed', true, created_at + interval '1 hour'
FROM orders WHERE order_number = 'ORD-2026-000007'
AND NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = orders.id AND h.from_status = 'PENDING' AND h.to_status = 'CONFIRMED');

INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT id, 'CONFIRMED', 'CANCELLED', 'Customer requested cancellation', 'seed', true, created_at + interval '2 hours'
FROM orders WHERE order_number = 'ORD-2026-000007'
AND NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = orders.id AND h.from_status = 'CONFIRMED' AND h.to_status = 'CANCELLED');

-- 5. CANCELLED order 10 (COD): PENDING -> CANCELLED
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT id, 'PENDING', 'CANCELLED', 'Customer cancelled', 'seed', true, created_at + interval '30 minutes'
FROM orders WHERE order_number = 'ORD-2026-000010'
AND NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = orders.id AND h.from_status = 'PENDING' AND h.to_status = 'CANCELLED');

-- 6. CANCELLED order 12 (REFUNDED): full chain -> CANCELLED
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT id, 'PENDING', 'CONFIRMED', 'Payment received', 'seed', true, created_at + interval '1 hour'
FROM orders WHERE order_number = 'ORD-2026-000012'
AND NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = orders.id AND h.from_status = 'PENDING' AND h.to_status = 'CONFIRMED');

INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT id, 'CONFIRMED', 'PROCESSING', 'Order confirmed', 'seed', false, created_at + interval '2 hours'
FROM orders WHERE order_number = 'ORD-2026-000012'
AND NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = orders.id AND h.from_status = 'CONFIRMED' AND h.to_status = 'PROCESSING');

INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT id, 'PROCESSING', 'SHIPPED', 'Shipped to carrier', 'seed', true, created_at + interval '1 day'
FROM orders WHERE order_number = 'ORD-2026-000012'
AND NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = orders.id AND h.from_status = 'PROCESSING' AND h.to_status = 'SHIPPED');

INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT id, 'SHIPPED', 'DELIVERED', 'Delivered to customer', 'seed', true, created_at + interval '3 days'
FROM orders WHERE order_number = 'ORD-2026-000012'
AND NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = orders.id AND h.from_status = 'SHIPPED' AND h.to_status = 'DELIVERED');

INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT id, 'DELIVERED', 'CANCELLED', 'Refunded and cancelled', 'seed', true, created_at + interval '4 days'
FROM orders WHERE order_number = 'ORD-2026-000012'
AND NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = orders.id AND h.from_status = 'DELIVERED' AND h.to_status = 'CANCELLED');

-- 7. CANCELLED order 14 (ONLINE): PENDING -> CONFIRMED -> CANCELLED
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT id, 'PENDING', 'CONFIRMED', 'Payment received', 'seed', true, created_at + interval '1 hour'
FROM orders WHERE order_number = 'ORD-2026-000014'
AND NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = orders.id AND h.from_status = 'PENDING' AND h.to_status = 'CONFIRMED');

INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT id, 'CONFIRMED', 'CANCELLED', 'Customer cancelled', 'seed', true, created_at + interval '2 hours'
FROM orders WHERE order_number = 'ORD-2026-000014'
AND NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = orders.id AND h.from_status = 'CONFIRMED' AND h.to_status = 'CANCELLED');

-- 8. SHIPPED order 8: PENDING -> CONFIRMED -> PROCESSING -> SHIPPED
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT id, 'PENDING', 'CONFIRMED', 'Payment received', 'seed', true, created_at + interval '1 hour'
FROM orders WHERE order_number = 'ORD-2026-000008'
AND NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = orders.id AND h.from_status = 'PENDING' AND h.to_status = 'CONFIRMED');

INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT id, 'CONFIRMED', 'PROCESSING', 'Order confirmed', 'seed', false, created_at + interval '2 hours'
FROM orders WHERE order_number = 'ORD-2026-000008'
AND NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = orders.id AND h.from_status = 'CONFIRMED' AND h.to_status = 'PROCESSING');

INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT id, 'PROCESSING', 'SHIPPED', 'Shipped to carrier', 'seed', true, created_at + interval '1 day'
FROM orders WHERE order_number = 'ORD-2026-000008'
AND NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = orders.id AND h.from_status = 'PROCESSING' AND h.to_status = 'SHIPPED');

-- 9. PROCESSING order 13: PENDING -> CONFIRMED -> PROCESSING
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT id, 'PENDING', 'CONFIRMED', 'Payment received', 'seed', true, created_at + interval '1 hour'
FROM orders WHERE order_number = 'ORD-2026-000013'
AND NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = orders.id AND h.from_status = 'PENDING' AND h.to_status = 'CONFIRMED');

INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT id, 'CONFIRMED', 'PROCESSING', 'Order confirmed', 'seed', false, created_at + interval '2 hours'
FROM orders WHERE order_number = 'ORD-2026-000013'
AND NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = orders.id AND h.from_status = 'CONFIRMED' AND h.to_status = 'PROCESSING');

-- 10. CONFIRMED order 15: PENDING -> CONFIRMED
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT id, 'PENDING', 'CONFIRMED', 'Payment received', 'seed', true, created_at + interval '1 hour'
FROM orders WHERE order_number = 'ORD-2026-000015'
AND NOT EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id = orders.id AND h.from_status = 'PENDING' AND h.to_status = 'CONFIRMED');