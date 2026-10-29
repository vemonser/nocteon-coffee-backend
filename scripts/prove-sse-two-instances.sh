#!/usr/bin/env bash
#
# prove-sse-two-instances.sh
#
# Reproducible proof that a notification created on instance B reaches an SSE
# stream held on instance A exactly once when the Redis pub/sub transport is in
# use (the default).
#
# What it proves
#   - app-a (port 8081) and app-b (port 8082) are two separate JVMs sharing one
#     Postgres and one Redis.
#   - A client subscribed to /api/dashboard/notifications/stream on app-a receives
#     `event: notification` and `event: unread-count` for a contact message that
#     was created on app-b.
#   - The notification event is delivered exactly once (no duplicate from the
#     publisher's own echo, no missing delivery).
#
# Prerequisites
#   - docker + docker compose v2 on PATH
#   - a populated .env (copy from .env.example) and the demo seed data loaded
#     (the default `db`/`redis` services are started by this script)
#   - ports 8081 and 8082 free
#   - admin credentials: override with ADMIN_EMAIL / ADMIN_PASSWORD if needed
#     (defaults match the demo seed: admin@aurum.local / Admin@123)
#
# Usage
#   ./scripts/prove-sse-two-instances.sh
#
# Exit codes: 0 = all assertions passed, non-zero = build/startup/assertion failure.
# Secrets (the bearer token) are kept in shell variables and never printed.

set -euo pipefail

# ── configuration ──────────────────────────────────────────────────────────────
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@aurum.local}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin@123}"
BASE_A="http://localhost:8081"
BASE_B="http://localhost:8082"
IMAGE_TAG="nocteon-api:sse-proof"
STREAM_TMP=""

cleanup() {
    if [[ -n "${STREAM_PID:-}" ]]; then
        kill "${STREAM_PID}" 2>/dev/null || true
        wait "${STREAM_PID}" 2>/dev/null || true
    fi
    if [[ -n "${STREAM_TMP:-}" && -f "${STREAM_TMP}" ]]; then
        rm -f "${STREAM_TMP}"
    fi
    docker rm -f nocteon-sse-a nocteon-sse-b >/dev/null 2>&1 || true
}
trap cleanup EXIT

# ── 1. build the image ─────────────────────────────────────────────────────────
echo "[1/6] building application image (docker compose build app)"
docker compose build app

echo "[1/6] tagging built image as ${IMAGE_TAG}"
IMAGE_ID="$(docker compose images -q app | head -n 1)"
if [[ -z "${IMAGE_ID}" ]]; then
    echo "FAIL: could not resolve the image produced by 'docker compose build app'" >&2
    exit 1
fi
docker tag "${IMAGE_ID}" "${IMAGE_TAG}"
export SSE_PROOF_IMAGE="${IMAGE_TAG}"

# ── 2. make sure db + redis are up ─────────────────────────────────────────────
echo "[2/6] starting db + redis"
docker compose up -d --wait db redis

# ── 3. start the two extra instances ───────────────────────────────────────────
echo "[3/6] starting app-a (:8081) and app-b (:8082)"
docker compose -f docker-compose.yml -f docker-compose.sse-proof.yml \
    up -d --wait app-a app-b

# ── 4. log in once (on instance A) ─────────────────────────────────────────────
echo "[4/6] logging in as admin"
LOGIN_BODY="$(curl -sS -X POST "${BASE_A}/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"identifier\":\"${ADMIN_EMAIL}\",\"password\":\"${ADMIN_PASSWORD}\"}")"
TOKEN="$(printf '%s' "${LOGIN_BODY}" | jq -r '.data.accessToken // empty')"
if [[ -z "${TOKEN}" ]]; then
    echo "FAIL: login did not return an access token" >&2
    printf '%s\n' "${LOGIN_BODY}" >&2
    exit 1
fi
# TOKEN is intentionally never echoed.

# ── 5. open the stream on A, then create a contact message on B ────────────────
echo "[5/6] opening SSE stream on instance A"
STREAM_TMP="$(mktemp)"
curl -sS -N -H "Authorization: Bearer ${TOKEN}" \
    "${BASE_A}/api/dashboard/notifications/stream" > "${STREAM_TMP}" 2>&1 &
STREAM_PID=$!

# give the stream a moment to send its `ready` event
sleep 1

echo "[5/6] creating a contact message on instance B"
curl -sS -X POST "${BASE_B}/api/contact" \
    -H 'Content-Type: application/json' \
    -d '{"name":"SSE Proof","email":"sse-proof@example.com","subject":"sse proof","message":"trigger notification"}' \
    >/dev/null

# ── 6. wait for the events and assert ───────────────────────────────────────────
echo "[6/6] waiting for events on instance A (up to 5s)"
for _ in $(seq 1 50); do
    n="$(grep -Ec '^event:[[:space:]]*notification[[:space:]]*$' "${STREAM_TMP}" || true)"
    u="$(grep -Ec '^event:[[:space:]]*unread-count[[:space:]]*$' "${STREAM_TMP}" || true)"
    if [[ "${n}" -ge 1 && "${u}" -ge 1 ]]; then
        break
    fi
    sleep 0.1
done

# stop the stream reader
kill "${STREAM_PID}" 2>/dev/null || true
wait "${STREAM_PID}" 2>/dev/null || true
STREAM_PID=""

n="$(grep -Ec '^event:[[:space:]]*notification[[:space:]]*$' "${STREAM_TMP}" || true)"
u="$(grep -Ec '^event:[[:space:]]*unread-count[[:space:]]*$' "${STREAM_TMP}" || true)"
n_idx="$(grep -En '^event:[[:space:]]*notification[[:space:]]*$' "${STREAM_TMP}" | head -n 1 | cut -d: -f1)"
u_idx="$(grep -En '^event:[[:space:]]*unread-count[[:space:]]*$' "${STREAM_TMP}" | head -n 1 | cut -d: -f1)"

fail=0
if [[ "${n}" -ne 1 ]]; then
    echo "FAIL: expected exactly 1 'notification' event on instance A, got ${n}" >&2
    fail=1
fi
if [[ "${u}" -lt 1 ]]; then
    echo "FAIL: expected at least 1 'unread-count' event on instance A, got ${u}" >&2
    fail=1
fi
if [[ -z "${n_idx}" || -z "${u_idx}" || "${n_idx}" -ge "${u_idx}" ]]; then
    echo "FAIL: 'notification' did not arrive before 'unread-count' (n_idx=${n_idx:-none}, u_idx=${u_idx:-none})" >&2
    fail=1
fi

if [[ "${fail}" -ne 0 ]]; then
    echo "--- captured stream ---" >&2
    cat "${STREAM_TMP}" >&2 || true
    exit 1
fi

echo "PASS: instance A received exactly one 'notification' followed by 'unread-count' from instance B"
exit 0
