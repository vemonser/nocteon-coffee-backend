CREATE INDEX idx_orders_created_at ON orders(created_at);
CREATE INDEX idx_orders_payment_status_created_at ON orders(payment_status, created_at);
CREATE INDEX idx_orders_status_created_at ON orders(status, created_at);
CREATE INDEX idx_orders_payment_method_created_at ON orders(payment_method, created_at);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
