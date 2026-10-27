# P1.2 Authentication Completion — Change Contract

Task: Finish the authentication surface: invalidate sessions on password change/reset, normalize emails, stop enumeration, make lockout configurable, require verified provider emails before account linking, and replace the OAuth2 access token in the redirect URL with a one-time exchange code.

Source of truth: this file.

## Scope
- `UserService.changePassword` and `PasswordResetService.resetPassword`: increment `tokenVersion` and delete every refresh token.
- Email handling: case-insensitive repository lookups; registration/admin/OAuth2 store lower-case emails; signup email normalized.
- `resendVerificationEmail`: silent for unknown/already-verified emails (no existence oracle).
- Login lockout: threshold and duration come from `application.security.lockout.*`, not hardcoded 5/30.
- OAuth2 account linking: never link/create from an unverified email. Google requires `email_verified`; GitHub verifies via `/user/emails`.
- OAuth2 redirect: send a UUID one-time code (2-minute TTL, Redis, single-use) instead of the access token; add `POST /api/auth/oauth2/exchange`.
- Tests: password lifecycle, signup normalization, reset/change invalidation, GitHub verifier, one-time code, success-handler redirect, lockout config.
- Document the frontend callback contract.

## Non-goals
- No frontend implementation in this repository (the callback page must call the exchange endpoint).
- No change to JWT signing/TTLs or RBAC.
- No change to email templates/SMTP.

## Acceptance criteria
AC-01: Changing or resetting a password increments `token_version`, deletes refresh tokens, and makes the old refresh token unusable.
AC-02: Emails are stored/looked up case-insensitively; registration stores the normalized email and leaves the account unverified; resend-verification does not reveal unknown emails.
AC-03: OAuth2 account linking/creation only uses a provider-verified email; Google requires `email_verified`; GitHub uses `/user/emails` verified addresses and fails closed on provider errors.
AC-04: OAuth2 redirects with a one-time UUID code, never the access token; the exchange endpoint returns an access token and the code is single-use.
AC-05: Login lockout honours the configured threshold and duration.
AC-06: The full JDK 25 suite passes (765 tests); the Docker stack is healthy with settings/readiness 200; the exchange endpoint is present.
AC-07: The success handler contains no access-token query parameter.

Risk: HIGH
Blast radius: all authentication flows, OAuth2 callback contract, session invalidation
Validation: focused auth tests, full suite, Docker smoke, static redirect check
Rollback: revert commit; tokens/codes are short-lived, no schema change

## Frontend contract
OAuth2 success redirects to `/{locale}/oauth2/callback?code=<uuid>` and sets the refresh cookie. The callback page must POST `{"code":"..."}` to `/api/auth/oauth2/exchange` and use `data.accessToken`. The code expires in two minutes and is single-use.
