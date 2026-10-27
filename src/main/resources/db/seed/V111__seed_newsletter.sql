-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.

-- ============================================================
-- V114: Seed newsletter subscribers
-- ============================================================
-- The newsletter page and the broadcast audience need at least a
-- handful of subscribers to be useful. The broadcast tests also
-- check that the audience is non-empty.

INSERT INTO newsletter_subscribers (email, token_hash, subscribed, created_at, updated_at)
VALUES
  ('ahmed@example.com', 'seed-token-ahmed', TRUE, NOW(), NOW()),
  ('mohamed@example.com', 'seed-token-mohamed', TRUE, NOW(), NOW()),
  ('nour@example.com', 'seed-token-nour', TRUE, NOW(), NOW()),
  ('omar@example.com', 'seed-token-omar', TRUE, NOW(), NOW()),
  ('youssef@example.com', 'seed-token-youssef', TRUE, NOW(), NOW()),
  ('salma@example.com', 'seed-token-salma', TRUE, NOW(), NOW()),
  ('mahmoud@example.com', 'seed-token-mahmoud', TRUE, NOW(), NOW()),
  ('mariam@example.com', 'seed-token-mariam', TRUE, NOW(), NOW()),
  ('sara@example.com', 'seed-token-sara', TRUE, NOW(), NOW()),
  ('fatima@example.com', 'seed-token-fatima', TRUE, NOW(), NOW())
ON CONFLICT (email) DO NOTHING;
