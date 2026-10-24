-- V118: add auth_version column to users for stale-token detection (RBAC B4)
ALTER TABLE users ADD COLUMN IF NOT EXISTS auth_version INT NOT NULL DEFAULT 0;
