-- V120: store audit log JSON payloads as TEXT to avoid Hibernate jsonb binding issues
-- Rollback: ALTER TABLE audit_logs ALTER COLUMN old_values TYPE jsonb USING old_values::jsonb, etc.
ALTER TABLE audit_logs ALTER COLUMN old_values TYPE TEXT USING old_values::text;
ALTER TABLE audit_logs ALTER COLUMN new_values TYPE TEXT USING new_values::text;
ALTER TABLE audit_logs ALTER COLUMN metadata TYPE TEXT USING metadata::text;
