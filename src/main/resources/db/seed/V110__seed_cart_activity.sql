-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.

UPDATE carts SET last_activity_at = updated_at WHERE last_activity_at IS NULL;
