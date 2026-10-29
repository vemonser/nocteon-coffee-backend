# Two-instance SSE proof

## What this proves

`scripts/prove-sse-two-instances.sh` is a reproducible end-to-end check that the
notification SSE stream crosses JVM boundaries exactly once when the Redis
pub/sub transport is enabled (the default):

1. Two separate Spring Boot containers (`nocteon-sse-a` on `:8081`,
   `nocteon-sse-b` on `:8082`) share one Postgres (`db`) and one Redis.
2. A client subscribes to `GET /api/dashboard/notifications/stream` on instance A.
3. A contact message is created on instance B (public `POST /api/contact`), which
   fans out a notification to every active user with `contact:read` (the admin).
4. Instance A's stream receives `event: notification` followed by
   `event: unread-count`.
5. Exactly one `notification` event is delivered (no duplicate from the
   publisher's own echo, no missing delivery).

The notification row is still the source of truth and the client still polls, so a
dropped batch would cost latency, never correctness — but this script proves the
happy path does deliver, and delivers once.

## Exact commands

From the repository root, with Docker and a populated `.env` in place:

```bash
# sanity check the script itself
bash -n scripts/prove-sse-two-instances.sh

# run the proof (builds the image, starts db/redis if needed, runs the two
# extra containers, asserts, cleans up)
./scripts/prove-sse-two-instances.sh
```

The orchestrator can override the admin credentials without editing the script:

```bash
ADMIN_EMAIL=admin@aurum.local ADMIN_PASSWORD='Admin@123' ./scripts/prove-sse-two-instances.sh
```

## Expected output (success)

```
[1/6] building application image (docker compose build app)
...
[6/6] waiting for events on instance A (up to 5s)
PASS: instance A received exactly one 'notification' followed by 'unread-count' from instance B
```

Exit code `0`. The two extra containers are removed by the `EXIT` trap.

On any failed assertion the script prints `FAIL: ...`, dumps the captured stream
to stderr, exits non-zero, and still removes both containers.

## Reproducing the OLD (single-instance) behaviour

To show the difference, run instance B with the in-process transport so it no
longer publishes to Redis. Edit the override (or set the env) so that
`NOTIFICATIONS_STREAM_TRANSPORT=local` for `app-b` only, then re-run the script:

```bash
# quick, non-persistent demonstration:
NOTIFICATIONS_STREAM_TRANSPORT=local is not enough on its own — the value is baked
into each container via docker-compose.sse-proof.yml. Instead, temporarily change
app-b's environment in docker-compose.sse-proof.yml:
#
#   NOTIFICATIONS_STREAM_TRANSPORT: "local"
#
# and re-run:
./scripts/prove-sse-two-instances.sh
```

With `local` on instance B, the contact message still creates the notification
row (and instance B's own streams would see it), but instance A's stream receives
nothing: the script fails with `FAIL: expected at least 1 'unread-count' event on
instance A, got 0`. Revert the override to `redis` to restore the passing proof.

The same toggle is the production single-instance choice:
`NOTIFICATIONS_STREAM_TRANSPORT=local` keeps everything in-process and is correct
for one instance or a broker-less environment.

## Assumptions / things not verified without running Docker

- `docker compose build app` produces an image resolvable via
  `docker compose images -q app`; the script tags it `nocteon-api:sse-proof`.
- `docker compose up -d --wait` is available (Compose v2) and db/redis report
  healthy before the app containers start.
- Ports 8081/8082 and the container names `nocteon-sse-a`/`nocteon-sse-b` are
  free; stale containers are removed by the cleanup trap.
- The demo seed data is loaded (admin `admin@aurum.local` / `Admin@123` exists
  and holds `contact:read`). Override via `ADMIN_EMAIL`/`ADMIN_PASSWORD`.
- Event delivery typically completes within ~1 s; the script waits up to 5 s to
  avoid timing flakes. Slow CI machines may need a larger wait.
- The contact endpoint is rate-limited per IP; the script makes exactly one
  request, so it will not trip the limit unless the IP is already hot.
- No secrets are printed: the bearer token lives only in `${TOKEN}` and is never
  echoed. The captured stream (which contains no token) is only dumped on
  failure.
