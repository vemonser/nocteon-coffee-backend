-- ============================================================
-- V114: Set NOT_COLLECTED on cancelled COD orders
-- ============================================================
-- A cancelled cash-on-delivery order whose payment was never captured
-- cannot have "FAILED" as its payment status — money that was never
-- collected cannot have failed. The correct state is NOT_COLLECTED.
--
-- Orders 6 and 10 in the seed are CANCELLED + CASH_ON_DELIVERY with
-- payment_status = FAILED (order 6) or PENDING (order 10).

UPDATE orders
SET payment_status = 'NOT_COLLECTED'
WHERE status = 'CANCELLED'
  AND payment_method = 'CASH_ON_DELIVERY'
  AND payment_status IN ('PENDING', 'FAILED');