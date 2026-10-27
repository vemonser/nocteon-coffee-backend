# Redis Startup Hardening — Change Contract

Task: Make Redis configuration consistent across Spring Data and Bucket4j, verify connectivity at startup with a clear failure, close the Lettuce connection on shutdown, and prove the behaviour with tests and a Docker smoke check.

Source of truth: this file.

## Scope
- `RedisConfig`: build the Bucket4j `RedisClient` from the same URL / host / port / password / SSL / database / timeout settings as Spring Data, including `REDIS_URL` and `spring.data.redis.ssl.enabled`.
- Add a shared `StatefulRedisConnection<String, byte[]>` bean created at startup: connect + `PING`, log success, and throw a clear `Redis is unreachable...` error on failure with a short timeout.
- Close the connection and client on context shutdown via bean destroy methods.
- `RateLimiterService` consumes the shared connection instead of opening its own unmanaged one.
- Add `RedisConnectivityIntegrationTest` covering Spring Data's PING and a Bucket4j bucket.
- Document the hard startup dependency and the Redis env vars.

## Non-goals
- No change to rate-limit thresholds or bucket keys.
- No change to token blacklist semantics.
- No fallback/degraded mode when Redis is down; fail-fast remains intentional.

## Acceptance criteria
AC-01: Given a reachable Redis, when the context starts, then the startup PING succeeds and both the Spring Data connection and the Bucket4j shared connection work.
AC-02: The Bucket4j client honours `spring.data.redis.url`, `REDIS_URL`, password, SSL, database and timeout instead of a hand-rolled host/port-only URI.
AC-03: Given an unreachable Redis, when the context starts, then it fails with a clear `Redis is unreachable. Check REDIS_URL or REDIS_HOST/REDIS_PORT/...` message and does not start serving requests.
AC-04: The Docker stack remains healthy, `/actuator/health/readiness` returns 200, and `/api/settings` returns 200 with Redis connected.
AC-05: The full JDK 25 suite still passes (736 tests).

Risk: MEDIUM
Blast radius: Redis client construction, rate limiting, token blacklist, chat session storage
Validation: focused Redis tests, full suite, Docker compose health smoke, negative fail-fast check
Rollback: revert commit; the previous RedisConfig/RateLimiterService remain in git history

## Known operational note
Redis is a hard startup dependency. That is deliberate: the token blacklist and rate limiter are security controls, so starting without them would silently weaken auth. The error message names the env vars to check.
