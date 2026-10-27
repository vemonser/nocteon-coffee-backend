# P1.1 Auth Security — Change Contract

Task: Remove the Java-deserialization RCE surface from the OAuth2 authorization-request cookie, harden refresh-token reuse/state checks, and make rate-limit IP resolution work behind Render's proxy.

Source of truth: this file.

## Scope
- Replace `CookieOAuth2AuthorizationRequestRepository`'s Java serialization + `ObjectInputStream` with Redis-backed JSON and an opaque UUID cookie (`HttpOnly`, `SameSite=Lax`, `Secure` when the request is secure).
- Enable Tomcat `forward-headers-strategy=native` with its trusted internal-proxies valve (not Spring's framework filter) so `request.isSecure()` works behind Render without trusting forwarded headers from every direct client.
- Refresh tokens: reject disabled/locked/unverified users; on rotated-token reuse, increment `tokenVersion` (invalidating outstanding access tokens) and delete all refresh tokens.
- AuthController: use `ClientIpResolver` instead of raw `getRemoteAddr()` for register/login/email-status/resend/forgot/reset limits.
- ClientIpResolver: accept `X-Forwarded-For` (first valid entry) when the peer is a trusted proxy; `X-Real-IP` still wins. Consistent use in Auth/Newsletter/Contact controllers.
- Tests: OAuth2 repository (opaque id, round-trip, single-use, tampered, no Java blob), refresh rotation tokenVersion, X-Forwarded-For.

## Non-goals
- No frontend change for the OAuth2 redirect token-in-query issue; recorded as a known follow-up requiring frontend coordination.
- No change to JWT signing, token TTLs, or permission model.
- No change to password reset/verification token storage in this cycle.

## Acceptance criteria
AC-01: The OAuth2 cookie contains only an opaque UUID; the request round-trips through Redis JSON; a tampered cookie returns null without any Java deserialization; single-use removal deletes both cookie and Redis key.
AC-02: Reusing a rotated refresh token increments `token_version` and deletes all refresh tokens; disabled/locked/unverified users cannot refresh.
AC-03: Behind a trusted proxy, X-Forwarded-For's first valid IP is used; untrusted peers cannot spoof either header; X-Real-IP wins.
AC-04: The full JDK 25 suite passes (749 tests) and the Docker stack remains healthy with readiness/settings 200.
AC-05: No `ObjectInputStream`/`readObject` remains in the OAuth2 repository.

Risk: HIGH
Blast radius: authentication/OAuth2 login, refresh rotation, rate-limit identity
Validation: focused OAuth2/refresh/IP tests, full suite, Docker smoke, static no-deserialization check
Rollback: revert commit; tokens/cookies are short-lived, no schema change

## Known follow-up
`OAuth2SuccessHandler` still redirects with the access token in the query string. Moving it to a fragment or a one-time exchange code requires a coordinated frontend change; do not silently break the login callback.
