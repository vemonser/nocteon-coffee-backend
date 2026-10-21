-- ============================================================
-- V107: Seed orders, reviews, and contact messages
-- ============================================================

-- ----------------------------------------------------------
-- 1. Seed default orders with order numbers
-- ----------------------------------------------------------
-- Note: order_number format is ORD-YYYY-NNNNNN
-- We'll create 20 sample orders with various statuses

-- Order 1: User 1, Product Variant 1
INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
SELECT 1, 1, 'ORD-2026-000001', 'DELIVERED', 'PAID', 'ONLINE', 50.00, 120.50, 0.00, 'Test order 1', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000001')
LIMIT 1;

-- Order 2: User 2, Product Variant 2
INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
SELECT 2, 2, 'ORD-2026-000002', 'DELIVERED', 'PAID', 'ONLINE', 50.00, 85.00, 0.00, 'Test order 2', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000002')
LIMIT 1;

-- Order 3: User 3, Product Variant 3
INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
SELECT 3, 3, 'ORD-2026-000003', 'DELIVERED', 'PAID', 'ONLINE', 50.00, 95.25, 0.00, 'Test order 3', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000003')
LIMIT 1;

-- Order 4: User 4, Product Variant 1
INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
SELECT 4, 4, 'ORD-2026-000004', 'DELIVERED', 'PAID', 'ONLINE', 50.00, 110.00, 0.00, 'Test order 4', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000004')
LIMIT 1;

-- Order 5: User 5, Product Variant 2
INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
SELECT 5, 5, 'ORD-2026-000005', 'DELIVERED', 'PAID', 'ONLINE', 50.00, 75.00, 0.00, 'Test order 5', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000005')
LIMIT 1;

-- Order 6: User 1, Pending status
INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
SELECT 1, 1, 'ORD-2026-000006', 'PENDING', 'PENDING', 'CASH_ON_DELIVERY', 50.00, 60.00, 0.00, 'Pending order 1', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000006')
LIMIT 1;

-- Order 7: User 2, Cancelled status
INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
SELECT 2, 2, 'ORD-2026-000007', 'CANCELLED', 'FAILED', 'ONLINE', 50.00, 45.00, 0.00, 'Cancelled order 1', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000007')
LIMIT 1;

-- Order 8: User 3, Shipped status
INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
SELECT 3, 3, 'ORD-2026-000008', 'SHIPPED', 'PAID', 'ONLINE', 50.00, 130.00, 0.00, 'Shipped order 1', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000008')
LIMIT 1;

-- Order 9: User 4, Refunded status
INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
SELECT 4, 4, 'ORD-2026-000009', 'DELIVERED', 'REFUNDED', 'ONLINE', 50.00, 200.00, 50.00, 'Refunded order 1', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000009')
LIMIT 1;

-- Order 10: User 4, Pending payment
INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
SELECT 4, 5, 'ORD-2026-000010', 'PENDING', 'PENDING', 'CASH_ON_DELIVERY', 50.00, 80.00, 0.00, 'Pending payment order 1', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000010')
LIMIT 1;

-- Order 11: User 5, Delivered status
INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
SELECT 5, 1, 'ORD-2026-000011', 'DELIVERED', 'PAID', 'ONLINE', 50.00, 150.00, 0.00, 'Delivered order 1', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000011')
LIMIT 1;

-- Order 12: User 1, Cancelled + refunded (OrderStatus has no RETURNED value)
INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
SELECT 1, 2, 'ORD-2026-000012', 'CANCELLED', 'REFUNDED', 'ONLINE', 50.00, 115.00, 10.00, 'Refunded order 1', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000012')
LIMIT 1;

-- Order 12: User 2, Processing status
INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
SELECT 2, 3, 'ORD-2026-000013', 'PROCESSING', 'PAID', 'ONLINE', 50.00, 95.50, 0.00, 'Processing order 1', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000013')
LIMIT 1;

-- Order 13: User 3, Awaiting payment
INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
SELECT 3, 4, 'ORD-2026-000014', 'PENDING', 'PENDING', 'ONLINE', 50.00, 65.00, 0.00, 'Awaiting payment order 1', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000014')
LIMIT 1;

-- Order 14: User 4, Confirmed + pending payment (OrderStatus has no ON_HOLD value)
INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
SELECT 4, 5, 'ORD-2026-000015', 'CONFIRMED', 'PENDING', 'CASH_ON_DELIVERY', 50.00, 70.00, 0.00, 'Confirmed order 1', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000015')
LIMIT 1;

-- Order 15: User 5, Quality issue (but delivered)
INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
SELECT 5, 1, 'ORD-2026-000016', 'DELIVERED', 'PAID', 'ONLINE', 50.00, 175.00, 0.00, 'Quality issue resolved order 1', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000016')
LIMIT 1;

-- Order 15: User 1, Last order of the series
INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
SELECT 1, 1, 'ORD-2026-000017', 'DELIVERED', 'PAID', 'ONLINE', 50.00, 140.00, 0.00, 'Last test order 1', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000017')
LIMIT 1;

-- ----------------------------------------------------------
-- 2. Seed product reviews
-- ----------------------------------------------------------

-- Review 1: Product 1, User 1, rating 5
INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, is_approved, created_at, updated_at)
SELECT 1, 1, 5, 'Excellent coffee, rich flavor!', TRUE, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 1 AND user_id = 1)
LIMIT 1;

-- Review 2: Product 1, User 2, rating 4
INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, is_approved, created_at, updated_at)
SELECT 1, 2, 4, 'Great quality, will buy again.', TRUE, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 1 AND user_id = 2)
LIMIT 1;

-- Review 3: Product 2, User 3, rating 3
INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, is_approved, created_at, updated_at)
SELECT 2, 3, 3, 'Good but a bit expensive.', TRUE, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 2 AND user_id = 3)
LIMIT 1;

-- Review 4: Product 2, User 4, rating 5
INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, is_approved, created_at, updated_at)
SELECT 2, 4, 5, 'Amazing aroma and taste.', TRUE, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 2 AND user_id = 4)
LIMIT 1;

-- Review 5: Product 3, User 5, rating 2
INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, is_approved, created_at, updated_at)
SELECT 3, 5, 2, 'Not my favorite roast level.', TRUE, FALSE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 3 AND user_id = 5)
LIMIT 1;

-- Review 6: Product 3, User 1, rating 4
INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, is_approved, created_at, updated_at)
SELECT 3, 1, 4, 'Perfect for morning brew.', TRUE, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 3 AND user_id = 1)
LIMIT 1;

-- Review 6: Product 4, User 2, rating 5
INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, is_approved, created_at, updated_at)
SELECT 4, 2, 5, 'Highly recommended!', TRUE, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 4 AND user_id = 2)
LIMIT 1;

-- Review 7: Product 4, User 3, rating 3
INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, is_approved, created_at, updated_at)
SELECT 4, 3, 3, 'Decent coffee for the price.', TRUE, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 4 AND user_id = 3)
LIMIT 1;

-- Review 8: Product 5, User 4, rating 4
INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, is_approved, created_at, updated_at)
SELECT 5, 4, 4, 'Love the packaging and freshness.', TRUE, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 5 AND user_id = 4)
LIMIT 1;

-- Review 9: Product 5, User 5, rating 2
INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, is_approved, created_at, updated_at)
SELECT 5, 5, 2, 'Not my favorite roast level.', TRUE, FALSE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 5 AND user_id = 5)
LIMIT 1;

-- Review 10: Product 5, User 1, rating 5
INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, is_approved, created_at, updated_at)
SELECT 5, 1, 5, 'Perfect coffee for espresso!', TRUE, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 5 AND user_id = 1)
LIMIT 1;

-- Review 11: Product 6, User 4, rating 4
INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, is_approved, created_at, updated_at)
SELECT 6, 4, 4, 'Good value for the quality.', TRUE, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 6 AND user_id = 4)
LIMIT 1;

-- Review 12: Product 6, User 5, rating 5
INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, is_approved, created_at, updated_at)
SELECT 6, 5, 5, 'Best coffee I have tried this month!', TRUE, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 6 AND user_id = 5)
LIMIT 1;

-- Review 13: Product 1 (replaced), User 1, rating 3
INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, is_approved, created_at, updated_at)
SELECT 1, 1, 3, 'Decent coffee, nothing special.', TRUE, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 1 AND user_id = 1)
LIMIT 1;

-- Review 13: Product 2 (replaced), User 2, rating 4
INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, is_approved, created_at, updated_at)
SELECT 2, 2, 4, 'Good for daily drinking.', TRUE, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 2 AND user_id = 2)
LIMIT 1;

-- Review 15: Product 3 (replaced), User 3, rating 5
INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, is_approved, created_at, updated_at)
SELECT 3, 3, 5, 'Excellent beans, freshness guaranteed!', TRUE, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 3 AND user_id = 3)
LIMIT 1;

-- ----------------------------------------------------------
-- 3. Seed contact messages
-- ----------------------------------------------------------

-- Contact message 1: Unread
INSERT INTO contact_messages (name, email, subject, message, is_read, created_at, updated_at)
SELECT 'Ahmed Hassan'::VARCHAR, 'ahmed@test.com'::VARCHAR, 'Question about shipping'::VARCHAR,
    'Hi, I wanted to ask about shipping to Alexandria. What are the rates?'::VARCHAR,
    FALSE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM contact_messages WHERE email = 'ahmed@test.com')
LIMIT 1;

-- Contact message 2: Read
INSERT INTO contact_messages (name, email, subject, message, is_read, created_at, updated_at)
SELECT 'Fatima Ali'::VARCHAR, 'fatima@test.com'::VARCHAR, 'Product inquiry'::VARCHAR,
    'Hello, I am interested in the Ethiopia Yirgacheffe. Can you tell me more about the roast level?'::VARCHAR,
    TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM contact_messages WHERE email = 'fatima@test.com')
LIMIT 1;

-- Contact message 3: Unread
INSERT INTO contact_messages (name, email, subject, message, is_read, created_at, updated_at)
SELECT 'Mohamed Omar'::VARCHAR, 'mohamed@test.com'::VARCHAR, 'Order issue'::VARCHAR,
    'My order #12345 hasn''t arrived yet. Can you check the status?'::VARCHAR,
    FALSE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM contact_messages WHERE email = 'mohamed@test.com')
LIMIT 1;

-- Contact message 4: Read
INSERT INTO contact_messages (name, email, subject, message, is_read, created_at, updated_at)
SELECT 'Aisha Mahmoud'::VARCHAR, 'aisha@test.com'::VARCHAR, 'Wholesale request'::VARCHAR,
    'We run a cafe in Cairo and would like to order wholesale. Do you offer bulk pricing?'::VARCHAR,
    TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM contact_messages WHERE email = 'aisha@test.com')
LIMIT 1;

-- Contact message 5: Unread
INSERT INTO contact_messages (name, email, subject, message, is_read, created_at, updated_at)
SELECT 'Youssef Ibrahim'::VARCHAR, 'youssef@test.com'::VARCHAR, 'Feedback'::VARCHAR,
    'Just wanted to say I love your coffee! The packaging is beautiful too.'::VARCHAR,
    FALSE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM contact_messages WHERE email = 'youssef@test.com')
LIMIT 1;

-- ----------------------------------------------------------
-- Order items (line items) for the seeded orders above, so dashboard
-- aggregations (revenue by category/origin/roast level, top products)
-- are populated.
-- ----------------------------------------------------------
INSERT INTO order_items (order_id, product_variant_id, quantity, unit_price, total_price)
VALUES
    (1, 1, 2, 650.00, 1300.00),
    (1, 4, 1, 525.00, 525.00),
    (2, 2, 1, 689.00, 689.00),
    (2, 7, 2, 600.00, 1200.00),
    (3, 3, 1, 1378.00, 1378.00),
    (3, 10, 2, 450.00, 900.00),
    (4, 5, 2, 556.50, 1113.00),
    (4, 8, 1, 636.00, 636.00),
    (5, 6, 1, 1113.00, 1113.00),
    (5, 9, 2, 1272.00, 2544.00),
    (8, 1, 1, 650.00, 650.00),
    (8, 11, 2, 477.00, 954.00),
    (11, 4, 2, 525.00, 1050.00),
    (11, 12, 1, 954.00, 954.00),
    (13, 7, 1, 600.00, 600.00),
    (13, 8, 1, 636.00, 636.00),
    (16, 2, 2, 689.00, 1378.00),
    (16, 5, 1, 556.50, 556.50),
    (17, 3, 1, 1378.00, 1378.00),
    (17, 9, 1, 1272.00, 1272.00);

-- ----------------------------------------------------------
-- End of V107 seed data migration
-- ============================================================