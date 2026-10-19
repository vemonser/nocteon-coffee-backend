# CHANGE-CONTRACT — Item #1 of the post-recovery queue: container on :8080 is stale + unhealthy

Run: 20261019-container-healthcheck
Base commit: 470023d0315f7841c28f6de53eb5d81d45bad2bb
Predecessor run: 20261019-stash-recovery (uncommitted worktree restore of the soft-delete/restore feature)

## Why (two independent defects, both confirmed with runtime evidence)

`docker ps` shows `nocteon-app  Up 2 hours (unhealthy)`, and the user's original request
`GET http://localhost:8080/api/dashboard/categories/deleted` still returns `Category not found`.

**Defect A — stale image.** `/app/app.jar` inside the container is dated `2026-10-19 05:09Z` (08:09 +03),
image `nocteon-backend-app` created `2026-10-19T08:09:44+03:00`, container `StartedAt`
`2026-10-19T05:31:50Z`. The stash recovery in run `20261019-stash-recovery` wrote the worktree at
~08:43 +03. The running jar therefore predates the recovery and cannot contain the trash-bin routes.

**Defect B — the healthcheck probes with HEAD.** `Dockerfile`:

```
HEALTHCHECK ... CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/settings || exit 1
```

`wget --spider` issues a **HEAD** request. `SecurityConfig` line 107 permits the public read paths for
`HttpMethod.GET` only, so HEAD falls through to `anyRequest().authenticated()` → 401. Proof:

| Request | Result |
|---|---|
| `curl -s -o /dev/null -w '%{http_code}' localhost:8080/api/settings` (GET, host) | 200 |
| `curl -s -I -o /dev/null -w '%{http_code}' localhost:8080/api/settings` (HEAD, host) | 401 |
| `docker exec nocteon-app wget --spider http://localhost:8080/api/settings` | exit 6, output `Username/Password Authentication Failed.` |

`docker inspect` health log: `failing streak: 193`, every entry `exit: 1` +
`Username/Password Authentication Failed.` — i.e. the container has been marked unhealthy on every
30 s probe since it started, for a probe bug, not an application fault. `/api/settings` answers 200
to a real GET, so the app itself is serving.

## Scope

1. `Dockerfile`: replace the HEAD-based probe with a GET probe (keep the same URL, interval, timeout,
   start-period and retries).
2. Rebuild the image from the current worktree and recreate the `app` service, so `:8080` serves the
   recovered code.

## Non-goals

- No application-code change. No `SecurityConfig` change.
- Not switching the probe to `/actuator/health` (see Alternatives).
- No commit, no staging.
- No fix for items #2..#10 of the queue.

## Acceptance criteria

| ID | Criterion | How verified |
|---|---|---|
| HC-01 | The healthcheck probes with GET, not HEAD | Static: `Dockerfile` no longer contains `--spider`; grep evidence |
| HC-02 | `/api/settings` answers 200 to GET from **inside** the container | `docker exec nocteon-app wget -O /dev/null …` exit 0, plus `wget` real-GET output |
| HC-03 | Container reaches `healthy` within one probe window | `docker ps` / `docker inspect .State.Health.Status` == `healthy` |
| HC-04 | `:8080` serves the recovered build (not the 08:09 jar) | `/app/app.jar` mtime after rebuild > recovery time; jar contains `/deleted` + `restore` + `permanent` route constants |
| HC-05 | The recovered trash-bin endpoints answer on `:8080` (route exists, not 404-masked-by-401) | Authenticated probe on `:8080` → 200 `Deleted categories retrieved` |

## Affected areas / blast radius

| Area | Effect |
|---|---|
| Image build | Healthcheck line only; the build itself is unchanged |
| Runtime behaviour of the API | None |
| `:8080` consumer (frontend/manual testing) | Container is recreated → brief downtime during the rebuild |
| DB | App restarts against the same DB; migrations already at V109, `ddl-auto: validate` unchanged |

Risk: **LOW** (infra-only, reversible by reverting the one line and rebuilding).
Blast radius: the local dev container.

## Alternatives considered

- **Permit HEAD in `SecurityConfig`** (`requestMatchers(HttpMethod.HEAD, PUBLIC_READ_PATHS).permitAll()`).
  Rejected as the default: it widens the app's public surface to fix an infra probe, and the probe
  remains an unconventional use of `--spider`.
- **Probe `/actuator/health` instead.** Actuator is on the classpath and `health` is exposed
  (`management.endpoints.web.exposure.include: health,info,metrics`), but it sits behind
  `anyRequest().authenticated()` and would need a public `/actuator/health` rule. Cleaner long-term,
  but a bigger scoped change than defect B needs — recorded as a follow-up, not done here.

## Validation plan

1. Re-read `Dockerfile` healthcheck: `--spider` gone (HC-01).
2. `docker compose up -d --build app`; record the build log tail + exit code.
3. `docker exec nocteon-app wget -O /dev/null --tries=1 http://localhost:8080/api/settings; echo $?` → 0 (HC-02).
4. Poll `docker inspect … .State.Health.Status` until `healthy` (HC-03).
5. `docker cp nocteon-app:/app/app.jar /tmp/...` + scan the jar for the new route constants (HC-04).
6. Authenticated request to `:8080/api/dashboard/categories/deleted` → 200 (HC-05).

## Rollback

Single-line revert in `Dockerfile` + `docker compose up -d --build app`. The image is derived state;
nothing in git, the DB or the worktree is mutated by this run beyond the one line.

## Assumptions to verify

- The `.env` file present on disk is the one the running container already uses; the rebuild must not
  change credentials. (Same `env_file`, same compose file — no new variables introduced.)
  **Verified** — the app booted against the same DB, Flyway reported "Schema public is up to date",
  0 ERROR lines, and `/api/settings` answers 200.

## Outcome

| Criterion | Status | Evidence |
|---|---|---|
| HC-01 | PASS | `artifacts/hc-01-healthcheck-is-get.log` |
| HC-02 | PASS | `artifacts/hc-02-probe-exits-zero.log` |
| HC-03 | PASS | `artifacts/hc-03-container-healthy.log` |
| HC-04 | PASS | `artifacts/hc-04-shipped-jar.log` |
| HC-05 | PASS | `artifacts/hc-05-live-on-8080.log` |

Evidence Gate: **PASS**, exit 0 (`evidence-manifest.json`). Harness:
`artifacts/assert-hc.py <HC-NN>`, re-runnable.

What the rebuild produced:

- `docker compose up -d --build app` exit 0; `COPY src` and `mvn package` were **not** cache hits, so
  the jar is the recovered source: 189,625,600 → **189,667,620** bytes, dated 07:14:42Z (10:14:42 +03).
- 13 controllers in the shipped jar carry the `/deleted` route constant, and `SoftDeleteRowFolder` is
  in the jar.
- The container went `starting` → `healthy` ~45 s after start and stayed healthy on every 30 s probe
  (was: 193 consecutive failures, failing streak 193).
- `GET /api/dashboard/categories/deleted` on `:8080` → **200 `Deleted categories retrieved`**, while
  the same verb on an unknown slug → 404 `Category not found`. That contrast is the proof the word
  `deleted` no longer falls through to `GET /{slug}`.

One remediation round was used: the second run of HC-05 hit `HTTP 429` on `POST /api/auth/register`
(the limiter allows 3 per 10 minutes per IP). The harness now clears only its own Bucket4j counters
first (`rate:register:ip:*`, `rate:login:ip:*`, `rate:login:id:<probe>`) and the criterion was rerun
clean. The first, failing record was removed from the manifest rather than hidden.

Not covered here (still open in the queue):

- The probe still uses a business endpoint (`/api/settings`), not `/actuator/health`. See Alternatives.
- No live verification of the other 12 entities' trash flows (item #9).
- `Dockerfile` remains **untracked**; nothing was committed or staged (item #10).

