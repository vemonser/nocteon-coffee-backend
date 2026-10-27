-- Auth integrity: case-insensitive email uniqueness and one account per
-- provider identity. The baseline's users_email_key is case-sensitive; the
-- application now lower-cases new emails, and this guards legacy rows too.
UPDATE users
SET email = lower(trim(email))
WHERE email <> lower(trim(email));

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_lower
    ON users (lower(email));

-- (user_id, provider) already exists; a provider identity must also not be
-- attached to two accounts (prevents duplicate OAuth2 links under concurrency).
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_linked_provider
    ON user_linked_accounts (provider, provider_id);
