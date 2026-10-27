-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.

-- V123: dev seed notifications for admin (Z5)
-- 2 pending reviews, 2 unread contact messages, 1 recent order, 1 low-stock variant; 3 unread.

INSERT INTO notifications (recipient_user_id, type, params, entity_type, entity_id, created_at, read_at, updated_at)
VALUES
 (1, 'review.pending', '{"productName":"Ethiopia Yirgacheffe","rating":5,"reviewerName":"sara"}', 'review', 1, NOW() - INTERVAL '1 hour', NULL, NOW() - INTERVAL '1 hour'),
 (1, 'review.pending', '{"productName":"Brazil Mogiana","rating":4,"reviewerName":"omar"}', 'review', 2, NOW() - INTERVAL '2 hours', NOW() - INTERVAL '1 hour', NOW() - INTERVAL '1 hour'),
 (1, 'contact.message', '{"subject":"Wholesale inquiry","senderName":"John Doe"}', 'contact', 1, NOW() - INTERVAL '3 hours', NULL, NOW() - INTERVAL '3 hours'),
 (1, 'contact.message', '{"subject":"Shipping question","senderName":"Jane Roe"}', 'contact', 2, NOW() - INTERVAL '4 hours', NOW() - INTERVAL '3 hours', NOW() - INTERVAL '3 hours'),
 (1, 'order.new', '{"orderNumber":"ORD-1001","customerName":"ahmed","total":1240.00}', 'order', 1, NOW() - INTERVAL '5 hours', NOW() - INTERVAL '4 hours', NOW() - INTERVAL '4 hours'),
 (1, 'stock.low', '{"productName":"Colombia Supremo","variantLabel":"SKU-COL-250","stock":3}', 'product_variant', 1, NOW() - INTERVAL '6 hours', NULL, NOW() - INTERVAL '6 hours');
