-- ============================================================
-- V107: Seed orders, reviews, and contact messages
-- ============================================================
-- This migration uses a DO block to capture generated order IDs
-- and use them for order_items, avoiding hardcoded FK issues.
-- Handles existing orders by selecting their IDs if they already exist.

DO $$
DECLARE
    o1 BIGINT; o2 BIGINT; o3 BIGINT; o4 BIGINT; o5 BIGINT;
    o6 BIGINT; o7 BIGINT; o8 BIGINT; o9 BIGINT; o10 BIGINT;
    o11 BIGINT; o12 BIGINT; o13 BIGINT; o14 BIGINT; o15 BIGINT;
    o16 BIGINT; o17 BIGINT;
BEGIN
    -- ----------------------------------------------------------
    -- 1. Seed default orders with order numbers
    -- ----------------------------------------------------------

    -- Order 1
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000001') THEN
        SELECT id INTO o1 FROM orders WHERE order_number = 'ORD-2026-000001';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (1, 1, 'ORD-2026-000001', 'DELIVERED', 'PAID', 'ONLINE', 50.00, 120.50, 0.00, 'Test order 1', NOW(), NOW())
        RETURNING id INTO o1;
    END IF;

    -- Order 2
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000002') THEN
        SELECT id INTO o2 FROM orders WHERE order_number = 'ORD-2026-000002';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (2, 2, 'ORD-2026-000002', 'DELIVERED', 'PAID', 'ONLINE', 50.00, 85.00, 0.00, 'Test order 2', NOW(), NOW())
        RETURNING id INTO o2;
    END IF;

    -- Order 3
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000003') THEN
        SELECT id INTO o3 FROM orders WHERE order_number = 'ORD-2026-000003';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (3, 3, 'ORD-2026-000003', 'DELIVERED', 'PAID', 'ONLINE', 50.00, 95.25, 0.00, 'Test order 3', NOW(), NOW())
        RETURNING id INTO o3;
    END IF;

    -- Order 4
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000004') THEN
        SELECT id INTO o4 FROM orders WHERE order_number = 'ORD-2026-000004';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (4, 4, 'ORD-2026-000004', 'DELIVERED', 'PAID', 'ONLINE', 50.00, 110.00, 0.00, 'Test order 4', NOW(), NOW())
        RETURNING id INTO o4;
    END IF;

    -- Order 5
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000005') THEN
        SELECT id INTO o5 FROM orders WHERE order_number = 'ORD-2026-000005';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (5, 5, 'ORD-2026-000005', 'DELIVERED', 'PAID', 'ONLINE', 50.00, 75.00, 0.00, 'Test order 5', NOW(), NOW())
        RETURNING id INTO o5;
    END IF;

    -- Order 6
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000006') THEN
        SELECT id INTO o6 FROM orders WHERE order_number = 'ORD-2026-000006';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (1, 1, 'ORD-2026-000006', 'PENDING', 'PENDING', 'CASH_ON_DELIVERY', 50.00, 60.00, 0.00, 'Pending order 1', NOW(), NOW())
        RETURNING id INTO o6;
    END IF;

    -- Order 7
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000007') THEN
        SELECT id INTO o7 FROM orders WHERE order_number = 'ORD-2026-000007';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (2, 2, 'ORD-2026-000007', 'CANCELLED', 'FAILED', 'ONLINE', 50.00, 45.00, 0.00, 'Cancelled order 1', NOW(), NOW())
        RETURNING id INTO o7;
    END IF;

    -- Order 8
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000008') THEN
        SELECT id INTO o8 FROM orders WHERE order_number = 'ORD-2026-000008';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (3, 3, 'ORD-2026-000008', 'SHIPPED', 'PAID', 'ONLINE', 50.00, 130.00, 0.00, 'Shipped order 1', NOW(), NOW())
        RETURNING id INTO o8;
    END IF;

    -- Order 9
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000009') THEN
        SELECT id INTO o9 FROM orders WHERE order_number = 'ORD-2026-000009';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (4, 4, 'ORD-2026-000009', 'DELIVERED', 'REFUNDED', 'ONLINE', 50.00, 200.00, 50.00, 'Refunded order 1', NOW(), NOW())
        RETURNING id INTO o9;
    END IF;

    -- Order 10
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000010') THEN
        SELECT id INTO o10 FROM orders WHERE order_number = 'ORD-2026-000010';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (4, 5, 'ORD-2026-000010', 'PENDING', 'PENDING', 'CASH_ON_DELIVERY', 50.00, 80.00, 0.00, 'Pending payment order 1', NOW(), NOW())
        RETURNING id INTO o10;
    END IF;

    -- Order 11
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000011') THEN
        SELECT id INTO o11 FROM orders WHERE order_number = 'ORD-2026-000011';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (5, 1, 'ORD-2026-000011', 'DELIVERED', 'PAID', 'ONLINE', 50.00, 150.00, 0.00, 'Delivered order 1', NOW(), NOW())
        RETURNING id INTO o11;
    END IF;

    -- Order 12
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000012') THEN
        SELECT id INTO o12 FROM orders WHERE order_number = 'ORD-2026-000012';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (1, 2, 'ORD-2026-000012', 'CANCELLED', 'REFUNDED', 'ONLINE', 50.00, 115.00, 10.00, 'Refunded order 1', NOW(), NOW())
        RETURNING id INTO o12;
    END IF;

    -- Order 13
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000013') THEN
        SELECT id INTO o13 FROM orders WHERE order_number = 'ORD-2026-000013';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (2, 3, 'ORD-2026-000013', 'PROCESSING', 'PAID', 'ONLINE', 50.00, 95.50, 0.00, 'Processing order 1', NOW(), NOW())
        RETURNING id INTO o13;
    END IF;

    -- Order 14
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000014') THEN
        SELECT id INTO o14 FROM orders WHERE order_number = 'ORD-2026-000014';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (3, 4, 'ORD-2026-000014', 'PENDING', 'PENDING', 'ONLINE', 50.00, 65.00, 0.00, 'Awaiting payment order 1', NOW(), NOW())
        RETURNING id INTO o14;
    END IF;

    -- Order 15
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000015') THEN
        SELECT id INTO o15 FROM orders WHERE order_number = 'ORD-2026-000015';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (4, 5, 'ORD-2026-000015', 'CONFIRMED', 'PENDING', 'CASH_ON_DELIVERY', 50.00, 70.00, 0.00, 'Confirmed order 1', NOW(), NOW())
        RETURNING id INTO o15;
    END IF;

    -- Order 16
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000016') THEN
        SELECT id INTO o16 FROM orders WHERE order_number = 'ORD-2026-000016';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (5, 1, 'ORD-2026-000016', 'DELIVERED', 'PAID', 'ONLINE', 50.00, 175.00, 0.00, 'Quality issue resolved order 1', NOW(), NOW())
        RETURNING id INTO o16;
    END IF;

    -- Order 17
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000017') THEN
        SELECT id INTO o17 FROM orders WHERE order_number = 'ORD-2026-000017';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (1, 1, 'ORD-2026-000017', 'DELIVERED', 'PAID', 'ONLINE', 50.00, 140.00, 0.00, 'Last test order 1', NOW(), NOW())
        RETURNING id INTO o17;
    END IF;

    -- ----------------------------------------------------------
    -- 2. Seed product reviews
    -- ----------------------------------------------------------

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 1, 1, 5, 'Excellent coffee, rich flavor!', TRUE, 'APPROVED', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 1 AND user_id = 1);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 1, 2, 4, 'Great quality, will buy again.', TRUE, 'APPROVED', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 1 AND user_id = 2);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 2, 3, 3, 'Good but a bit expensive.', TRUE, 'APPROVED', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 2 AND user_id = 3);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 2, 4, 5, 'Amazing aroma and taste.', TRUE, 'APPROVED', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 2 AND user_id = 4);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 3, 5, 2, 'Not my favorite roast level.', TRUE, 'PENDING', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 3 AND user_id = 5);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 3, 1, 4, 'Perfect for morning brew.', TRUE, 'APPROVED', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 3 AND user_id = 1);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 4, 2, 5, 'Highly recommended!', TRUE, 'APPROVED', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 4 AND user_id = 2);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 4, 3, 3, 'Decent coffee for the price.', TRUE, 'APPROVED', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 4 AND user_id = 3);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 5, 4, 4, 'Love the packaging and freshness.', TRUE, 'APPROVED', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 5 AND user_id = 4);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 5, 5, 2, 'Not my favorite roast level.', TRUE, 'PENDING', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 5 AND user_id = 5);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 5, 1, 5, 'Perfect coffee for espresso!', TRUE, 'APPROVED', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 5 AND user_id = 1);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 6, 2, 3, 'Decent coffee, nothing special.', TRUE, 'APPROVED', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 6 AND user_id = 2);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 6, 3, 4, 'Good for daily drinking.', TRUE, 'APPROVED', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 6 AND user_id = 3);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 6, 4, 5, 'Excellent beans, freshness guaranteed!', TRUE, 'APPROVED', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 6 AND user_id = 4);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 6, 5, 5, 'Best coffee I have tried this month!', TRUE, 'APPROVED', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 6 AND user_id = 5);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 1, 1, 3, 'Decent coffee, nothing special.', TRUE, 'APPROVED', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 1 AND user_id = 1);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 2, 2, 4, 'Good for daily drinking.', TRUE, 'APPROVED', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 2 AND user_id = 2);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 3, 3, 5, 'Excellent beans, freshness guaranteed!', TRUE, 'APPROVED', NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 3 AND user_id = 3);

    -- ----------------------------------------------------------
    -- 3. Seed contact messages
    -- ----------------------------------------------------------

    INSERT INTO contact_messages (name, email, subject, message, is_read, is_replied, reply_content, created_at, updated_at, status)
    SELECT 'Ahmed Hassan'::VARCHAR, 'ahmed@test.com'::VARCHAR, 'Question about shipping'::VARCHAR,
        'Hi, I wanted to ask about shipping to Alexandria. What are the rates?'::VARCHAR,
        FALSE, FALSE, NULL, NOW(), NOW(), 'NEW'
    WHERE NOT EXISTS (SELECT 1 FROM contact_messages WHERE email = 'ahmed@test.com');

    INSERT INTO contact_messages (name, email, subject, message, is_read, is_replied, reply_content, created_at, updated_at, status)
    SELECT 'Fatima Ali'::VARCHAR, 'fatima@test.com'::VARCHAR, 'Product inquiry'::VARCHAR,
        'Hello, I am interested in the Ethiopia Yirgacheffe. Can you tell me more about the roast level?'::VARCHAR,
        TRUE, FALSE, NULL, NOW(), NOW(), 'NEW'
    WHERE NOT EXISTS (SELECT 1 FROM contact_messages WHERE email = 'fatima@test.com');

    INSERT INTO contact_messages (name, email, subject, message, is_read, is_replied, reply_content, created_at, updated_at, status)
    SELECT 'Mohamed Omar'::VARCHAR, 'mohamed@test.com'::VARCHAR, 'Order issue'::VARCHAR,
        'My order #12345 hasn''t arrived yet. Can you check the status?'::VARCHAR,
        FALSE, FALSE, NULL, NOW(), NOW(), 'NEW'
    WHERE NOT EXISTS (SELECT 1 FROM contact_messages WHERE email = 'mohamed@test.com');

    INSERT INTO contact_messages (name, email, subject, message, is_read, is_replied, reply_content, created_at, updated_at, status)
    SELECT 'Aisha Mahmoud'::VARCHAR, 'aisha@test.com'::VARCHAR, 'Wholesale request'::VARCHAR,
        'We run a cafe in Cairo and would like to order wholesale. Do you offer bulk pricing?'::VARCHAR,
        TRUE, FALSE, NULL, NOW(), NOW(), 'NEW'
    WHERE NOT EXISTS (SELECT 1 FROM contact_messages WHERE email = 'aisha@test.com');

    INSERT INTO contact_messages (name, email, subject, message, is_read, is_replied, reply_content, created_at, updated_at, status)
    SELECT 'Youssef Ibrahim'::VARCHAR, 'youssef@test.com'::VARCHAR, 'Feedback'::VARCHAR,
        'Just wanted to say I love your coffee! The packaging is beautiful too.'::VARCHAR,
        FALSE, FALSE, NULL, NOW(), NOW(), 'NEW'
    WHERE NOT EXISTS (SELECT 1 FROM contact_messages WHERE email = 'youssef@test.com');

    -- ----------------------------------------------------------
    -- 4. Order items (line items) for the seeded orders above
    -- ----------------------------------------------------------
    INSERT INTO order_items (order_id, product_variant_id, quantity, unit_price, total_price)
    VALUES
        (o1, 1, 2, 650.00, 1300.00),
        (o1, 4, 1, 525.00, 525.00),
        (o2, 2, 1, 689.00, 689.00),
        (o2, 7, 2, 600.00, 1200.00),
        (o3, 3, 1, 1378.00, 1378.00),
        (o3, 10, 2, 450.00, 900.00),
        (o4, 5, 2, 556.50, 1113.00),
        (o4, 8, 1, 636.00, 636.00),
        (o5, 6, 1, 1113.00, 1113.00),
        (o5, 9, 2, 1272.00, 2544.00),
        (o8, 1, 1, 650.00, 650.00),
        (o8, 11, 2, 477.00, 954.00),
        (o11, 4, 2, 525.00, 1050.00),
        (o11, 12, 1, 954.00, 954.00),
        (o13, 7, 1, 600.00, 600.00),
        (o13, 8, 1, 636.00, 636.00),
        (o16, 2, 2, 689.00, 1378.00),
        (o16, 5, 1, 556.50, 556.50),
        (o17, 3, 1, 1378.00, 1378.00),
        (o17, 9, 1, 1272.00, 1272.00);

END $$;