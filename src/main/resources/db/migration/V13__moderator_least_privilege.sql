-- MODERATOR: least privilege for a content/community moderator.
--
-- Schema migrations stay below the seed range (V100+), so this is V13. A fresh
-- database applies V1..V13 before the seeds. A dev database that already ran
-- the seeds needs a ONE-OFF `SPRING_FLYWAY_OUT_OF_ORDER=true` to apply it.
--
-- The seeded role had 39 permissions, among them user:update (change any
-- account's e-mail → password reset → takeover, now blocked in code), the
-- whole catalogue write surface, journal publishing, cart notifications and
-- order updates (which include payment retries). Meanwhile it was missing
-- contact:read and notification:read, i.e. the two surfaces a moderator
-- actually works in.
--
-- This set is the product decision: see the content, moderate it, talk to
-- customers, and nothing else. ADMIN keeps every permission; CUSTOMER is
-- untouched.
--
-- Deliberately NOT granted:
--   user:*                account administration / identity changes
--   *:create, *:delete    catalogue and content ownership
--   product/category/…:update   merchandising
--   order:update          includes payment retry: a money operation
--   cart:manage, cart:notify    customer carts / outbound e-mail
--   settings:*, promo:*, shipping:*, broadcast:*, newsletter:*   admin-only
--
-- Reads of catalogue reference data are kept because the product pages render
-- those names; they are read-only and carry no PII.

DELETE FROM public.role_permissions WHERE role_name = 'MODERATOR';

INSERT INTO public.role_permissions (role_name, permission) VALUES
    ('MODERATOR', 'dashboard:read'),
    ('MODERATOR', 'order:read'),
    ('MODERATOR', 'cart:read_all'),
    ('MODERATOR', 'review:read'),
    ('MODERATOR', 'review:moderate'),
    ('MODERATOR', 'review:delete'),
    ('MODERATOR', 'contact:read'),
    ('MODERATOR', 'contact:update'),
    ('MODERATOR', 'notification:read'),
    ('MODERATOR', 'notification:update'),
    ('MODERATOR', 'product:read'),
    ('MODERATOR', 'category:read'),
    ('MODERATOR', 'origin:read'),
    ('MODERATOR', 'farm:read'),
    ('MODERATOR', 'roast_level:read'),
    ('MODERATOR', 'processing_method:read'),
    ('MODERATOR', 'coffee_variety:read'),
    ('MODERATOR', 'tasting_note:read'),
    ('MODERATOR', 'brewing_method:read'),
    ('MODERATOR', 'pairing:read'),
    ('MODERATOR', 'audit:read')
ON CONFLICT (role_name, permission) DO NOTHING;
