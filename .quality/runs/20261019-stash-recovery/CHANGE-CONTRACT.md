# CHANGE-CONTRACT — Recover the lost catalog soft-delete/restore/trash-bin work

Run: 20261019-stash-recovery
Base commit: 470023d0315f7841c28f6de53eb5d81d45bad2bb (`fix: atomic stock reservation kills the oversell race`)
Source of the recovered code: unreachable stash commit `2863fe1` ("WIP on fix/audit-findings: 470023d …", 2026-10-18 21:38 +0300)

## Why

`GET /api/dashboard/categories/deleted` returns `{"message":"Category not found"}`.

Root cause (confirmed, not a routing bug): the trash-bin feature was implemented in the working tree,
was **never committed**, and was then removed from the tracked files by a hard reset
(`git reflog`: five `reset: moving to HEAD` entries + a checkout to `main`/back).
The implementation now survives only as unreachable stash commits. New (untracked-week) files of the
same work are still on disk, which is why `SoftDeleteRowFolder` and `EntityInUseException` exist but
are referenced by nothing.

Evidence of loss:
- `git log --all -S'/deleted' -- '*.java'` → no commit ever contained the route.
- Stash `2863fe1` vs `HEAD` → 74 modified files, `+3142 / -168`.
- Current worktree vs stash → 68 source files missing the stash content.
- `SoftDeleteRowFolder.java` / `EntityInUseException.java` present but unreferenced anywhere.

## Scope

Restore the 2026-10-18 21:38 tracked-file state of the trash-bin feature
(`GET /dashboard/{entity}/deleted`, `PATCH /dashboard/{entity}/{id}/restore`,
`DELETE /dashboard/{entity}/{id}/permanent`) for every catalog-style entity plus journal and products,
by writing the stash content of the 69 files that the worktree has not independently moved on.

## Non-goals

- No new feature work, no refactor, no behaviour change beyond what the stash already contained.
- No commit. No staging. Worktree only (the user decides what to commit).
- No touching of files the worktree has since changed independently (see "Preserved" below).

## Preserved deliberately (stash would revert newer work)

| File | Why kept as-is |
|---|---|
| `docker-compose.yml` | Worktree adds an `app` service + env defaults (2026-10-19 08:19); stash is the older version. |
| `pom.xml` | Worktree pins Lombok 1.18.36 + explicit version; stash is the older 1.18.40 property-only form. |
| `common/enums/Permission.java` | Worktree only adds `AUDIT_READ("audit:read")`, required by the on-disk `audit/` module. The stash does not touch this file. |
| `db/migration/V3__*.sql`, `V4__*.sql` | Worktree content already equals the stash content. |
| `messages.properties`, `messages_ar.properties` | Worktree content already equals the stash content. |
| `auth/enums/Role.java` | Needs a **3-way merge**: stash grants `DASHBOARD_READ` to MODERATOR and drops `USER_READ`/`USER_UPDATE` from CUSTOMER; worktree adds `AUDIT_READ` to MODERATOR. The MODERATOR additions were kept. **The CUSTOMER drop was NOT applied** — see "Deviations during execution" #3. |

## Acceptance criteria

- **AC-01** `GET /api/dashboard/categories/deleted` exists and returns the trash-bin listing instead of `Category not found`.
- **AC-02** `PATCH /api/dashboard/categories/{id}/restore` clears `deleted_at` and the row reappears in ordinary queries.
- **AC-03** `DELETE /api/dashboard/categories/{id}/permanent` removes the row.
- **AC-04** The same three routes exist for every catalog entity the stash covered (farms, origins, pairings, brewing methods, processing methods, roast levels, tasting notes, coffee varieties), plus journal and products.
- **AC-05** The project compiles (`./mvnw compile`) with the restored code — i.e. the restored modified files are consistent with the already-on-disk new files.
- **AC-06** No newer work is lost: the files the worktree had independently moved on keep their current content, `Role.java` keeps MODERATOR's `DASHBOARD_READ` (stash) and `AUDIT_READ` (worktree), and CUSTOMER keeps the `USER_READ`/`USER_UPDATE` grants it held at HEAD.

## Deviations during execution

1. **JDK.** A bare `./mvnw` resolves JDK 27 from `JAVA_HOME` and Lombok generates nothing (414 "cannot find symbol" errors). The repo pins `java 21.0.2` in `.mise.toml`. Every build/test/run below used `JAVA_HOME=~/.local/share/mise/installs/java/21.0.2`. This is an environment precondition, not a code defect.
2. **`ProductRepository.java` had to be repaired.** The stash stacked two `@Query` annotations on `findAllDashboard` (lines 89–111 as restored). A non-repeatable annotation repeated is a compile error, which aborts javac and cascades into hundreds of missing-Lombok-getter errors. The stale first annotation (no `pairings` fetch) was deleted; the stash's intended `LEFT JOIN FETCH p.pairings` version was kept. This is the most likely reason the WIP was never committed.
3. **Role.java: the stash's CUSTOMER edit was dropped.** The stash removed `USER_READ`/`USER_UPDATE` from CUSTOMER. `AddressController` gates the whole customer self-service address book (`/api/addresses`, including create/update/delete) on `user:read`/`user:update`, and authorities come from `Role.getPermissions()`, so applying that edit 403s every customer's address book and breaks checkout address selection. The hole the stash was closing is on `AdminUserController` (`/api/dashboard/users` requires only `user:read`, and `SecurityConfig` requires only `.authenticated()` for `/api/dashboard/**`); that is a separate, pre-existing defect and is recorded as an open risk rather than fixed here. Keeping HEAD's grant means the recovery does not regress the storefront.

## Risk / blast radius

- **Risk:** MEDIUM. 69 tracked source files are overwritten in the worktree.
- **Blast radius:** every dashboard catalog endpoint (routing, permissions, repositories) plus journal and products.
- **Mitigation:** full worktree tarball + tracked-diff patch taken before the first write; the stash commits are anchored with `recover/*` tags so `git gc` cannot collect them.

## Rollback

- `tar -xzf /tmp/opencode/recovery/worktree-before-20261019.tar.gz -C <repo>` restores the pre-recovery worktree.
- `git diff HEAD` baseline: `/tmp/opencode/recovery/worktree-before-recovery.patch`.
- `git restore --source=470023d -- <path>` reverts any single file to the last commit.

## Validation plan

1. `./mvnw -q compile` → AC-05.
2. Inspect the restored controllers for the three routes across entities → AC-01..AC-04.
3. Confirm the preserved files are byte-identical to their pre-recovery content → AC-06.
4. `git diff --stat` review to confirm only the intended 69 + 1 files moved.
5. Evidence Gate (`/factory/evidence`) must exit 0 before this is called complete.

## Assumptions to verify

- **VERIFIED**: the server the user queried was **not** this working tree. `ps` shows `java -jar /app/app.jar` — a Docker container (`nocteon-app`, image `nocteon-backend-app`) built from an older source state and currently reported **unhealthy**. Nothing short of rebuilding that image will put the recovered code behind :8080. The recovery was verified against a local `./mvnw spring-boot:run --server.port=8081` instead.

## Independent review (adversarial-reviewer run after validation)

Verdict: NEEDS_CHANGES. Findings and dispositions:

| # | Severity | Finding | Disposition |
|---|---|---|---|
| 1 | CRITICAL | Restoring the stash's CUSTOMER edit 403s every customer's `/api/addresses` | **Fixed** by deviation #3 above; the CUSTOMER grants were kept |
| 2 | HIGH | `findAllDashboard` now fetch-joins `p.pairings` under a `Pageable`, so Hibernate paginates in memory (`HHH90003004`); correct results, unbounded work on a large catalog | **Open risk** — this is the stash's own design; kept for fidelity. Fix later by dropping the fetch and relying on `@BatchSize`, or by paging ids first |
| 3 | MEDIUM | The default `sort=createdAt` has no comparator case and `findAllDeletedNative` has no `ORDER BY`, so paged trash listings can repeat or drop rows | **Open risk** (stash defect, not caused by the recovery) |
| 4 | MEDIUM | The journal-category trash listing ignores `sort`/`direction` while its sibling post listing honours them | **Open risk** |
| 5 | MEDIUM | `/deleted` handlers take `@ModelAttribute LookupFilterRequest` without `@Valid`, so `?page=-1` reaches `subList` and 500s | **Pre-existing repo-wide pattern** (every dashboard list endpoint shares it) |
| 6 | MEDIUM | `RoastLevelNotFoundException` uses key `error.farm.notFound` and `PairingNotFoundException` uses `error.brewingMethod.notFound`; wrong operator-facing message on the new not-found paths | **Pre-existing** message keys, newly reachable — left alone to keep scope |
| 7 | LOW | `FarmService` has a duplicate import (compiles) | Left alone |
| 8 | LOW | A `JournalService` comment cites a `SoftDeleteRepositoryIntegrationTest` that does not exist | Left alone |
| 9 | — | Only the categories trash flow is live-verified; the other entities' trash listings are covered by static route checks, not execution | Recorded as the honest coverage boundary |
| 10 | — | `AuthorizationConfigTest` asserts trash gating is disjoint from CUSTOMER, not that it equals `{entity}:delete` | Test-coverage gap, not a defect |

