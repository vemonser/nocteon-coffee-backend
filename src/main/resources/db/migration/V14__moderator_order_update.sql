-- MODERATOR may manage orders (status, notes, shipment, refunds, payment retry).
--
-- Product decision: a moderator runs the order queue. `order:update` is the
-- single grant that covers the dashboard status/notes/shipment writes and the
-- two money operations (refund, payment retry). If those should be separate,
-- split them into dedicated permissions rather than dropping this one.
--
-- Numbered below the seed range (V100+) on purpose; a dev database that has
-- already applied the seeds needs a one-off SPRING_FLYWAY_OUT_OF_ORDER=true.

INSERT INTO public.role_permissions (role_name, permission)
VALUES ('MODERATOR', 'order:update')
ON CONFLICT (role_name, permission) DO NOTHING;
