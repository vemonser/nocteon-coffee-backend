# Baseline Test Hygiene Report

Scope: fix the flaky `ShippingZoneServiceTest` and unblock `NocteonApiApplicationTests.contextLoads`.
Command used throughout: `mise exec java@21.0.2 -- ./mvnw test ...` (a bare `./mvnw` runs under
JDK 27 and dies with `ExceptionInInitializerError: com.sun.tools.javac.tree.EndPosTable`).

---

## 1. Root cause of the ShippingZone flakiness

**The premise was correct, but the mechanism is one level deeper than "a stub goes unused".**
The stub is not merely unused — the test data is *nondeterministically ordered*, so the service
legitimately calls a **different argument** on an already-stubbed method, which Mockito's
`STRICT_STUBS` rejects with `PotentialStubbingProblem`.

### Chain of causation

`ShippingZoneService.validateNoCityConflict` iterates the request's city set one city at a time
and issues one repository call per city:

- `src/main/java/com/nocteon/nocteon_api/shippingZone/service/ShippingZoneService.java:87-90`
  ```java
  for (String city : cities) {
      boolean exists = excludeZoneId == null
              ? shippingZoneRepository.existsByCitiesContaining(city)
              : shippingZoneRepository.existsByCitiesContainingAndIdNot(city, excludeZoneId);
      if (exists) { throw new CityAlreadyAssignedException(city); }
  }
  ```

The test fed it a `Set.of("Cairo", "Alexandria")`. `Set.of` with exactly two elements returns an
`ImmutableCollections.Set12`, whose two arguments are **swapped based on a per-JVM random
`SALT`** (`ImmutableCollections.set2`). Iteration order is therefore a coin flip on every fresh
JVM — and surefire uses the default `forkCount=1, reuseForks=true`, so the whole suite shares one
JVM and one `SALT`.

Verified empirically on this machine (12 JVM launches, JDK 21.0.2):

```
Set12 -> [Alexandria, Cairo]     Set12 -> [Cairo, Alexandria]
Set12 -> [Alexandria, Cairo]     Set12 -> [Cairo, Alexandria]     ... (~50/50)
```

The test stubbed only one of the two arguments:

- `ShippingZoneServiceTest.java:174` (pre-fix) — `existsByCitiesContainingAndIdNot("Alexandria", 1L)` → `true`

So when `SALT` puts **"Cairo" first**, `validateNoCityConflict` calls
`existsByCitiesContainingAndIdNot("Cairo", 1L)` — unstubbed, but with arguments that mismatch the
existing stub on the same method. Mockito's `DefaultStubbingLookupListener` treats that as a
stubbing-argument mismatch and throws before the loop ever reaches "Alexandria". Captured verbatim:

```
org.mockito.exceptions.misusing.PotentialStubbingProblem:
Strict stubbing argument mismatch. Please check:
 - this invocation of 'existsByCitiesContainingAndIdNot' method:
    shippingZoneRepository.existsByCitiesContainingAndIdNot("Cairo", 1L)
    -> at ...ShippingZoneService.validateNoCityConflict(ShippingZoneService.java:90)
 - has following stubbing(s) with different arguments:
    1. shippingZoneRepository.existsByCitiesContainingAndIdNot("Alexandria", 1L); stubbed with: [Returns: true]
      -> at ...ShippingZoneServiceTest$CityConflictValidation.updateThrowsWhenAddingACityOwnedByADifferentZone(ShippingZoneServiceTest.java:174)
...
but was: com.nocteon.nocteon_api.shippingZone.exception.CityAlreadyAssignedException
```

Measured pre-fix flakiness: **1 failure in 6 consecutive class runs** (and 1 in 2 on a second
sample). ~50% as predicted.

### Other tests in the class — checked, not affected

`Set.of` order dependence only bites when *some* iterated values are stubbed and others are not.
Every other multi-city test in the class stubs **all** its cities, so they are order-independent
and were left untouched:

| Test | Cities | Stubs | Verdict |
|---|---|---|---|
| `createSucceedsWhenAllCitiesAreUnclaimed` | Cairo, Giza | both → `false` | both consumed either order — safe |
| `updateExcludesOwnZoneIdFromConflictCheck` | Cairo, Giza | both → `false` | both consumed either order — safe |
| `createThrowsWhenCityAlreadyBelongsToAnotherZone` | Cairo (1) | 1 of 1 | single element — safe |
| `updateThrowsWhenZoneNotFound` | Nowhere (1) | n/a (throws before validation) | safe |

## 2. The fix applied

Narrow, single-method, `STRICT_STUBS` left intact on the whole class. No `lenient()`, no
`@MockitoSettings`, no class-wide relaxation.

In `ShippingZoneServiceTest$CityConflictValidation.updateThrowsWhenAddingACityOwnedByADifferentZone`
(`src/test/java/com/nocteon/nocteon_api/shipping/service/ShippingZoneServiceTest.java:157-187`):

1. Replaced the order-randomized `Set.of("Cairo", "Alexandria")` with a `LinkedHashSet` that pins
   the iteration order to Cairo → Alexandria.
2. Added the stub that was genuinely missing — `existsByCitiesContainingAndIdNot("Cairo", 1L)` →
   `false` — because the test's own fixture says "Cairo" is zone #1's city and `AndIdNot(1L)`
   excludes it, so it is *not* a conflict. Both stubs are now consumed on every single run.
3. Left an inline comment explaining why `Set.of` must not come back.

### Why this is the right fix and not `lenient()`

`lenient()` on the "Alexandria" stub *would* have made the test pass, because a lenient stub is
excluded from Mockito's arg-mismatch report and the stray `"Cairo"` call would silently default to
`false`. That is strictly worse:

- it makes correctness depend on an unstubbed call returning the default `false`, which is
  accidental rather than asserted;
- it leaves the test's outcome still sensitive to `SALT` — it only stops Mockito from noticing;
- it would be the first step down the road to class-wide `LENIENT`, which masks real problems in
  the other tests.

The deterministic-data fix removes the nondeterminism at its source and makes the test express what
it actually means: *zone #1's own city is not a conflict, but another zone's city is.*

## 3. How `contextLoads` was fixed

`KeyConfig` (`src/main/java/com/nocteon/nocteon_api/auth/config/KeyConfig.java:20-42`) reads
`@Value("${JWT_PRIVATE_KEY:}")` / `@Value("${JWT_PUBLIC_KEY:}")` first and only falls back to the
classpath PEMs when those are blank. The PEMs are gitignored (`.gitignore` lines 36-37:
`src/main/resources/keys/` and `*.pem`) and confirmed absent from both the source tree and
`target/classes` — so on a clean checkout the classpath fallback always fails.

Fix — no key material committed, no base64 blob in a properties file:

- New `src/test/java/com/nocteon/nocteon_api/support/TestJwtKeys.java`: generates an RSA-2048
  `KeyPair` once per JVM in memory, PEM-wraps the private key as PKCS#8 and the public key as X.509,
  and registers them via `DynamicPropertyRegistry`.
- `src/test/java/com/nocteon/nocteon_api/NocteonApiApplicationTests.java` gained a
  `@DynamicPropertySource` method calling `TestJwtKeys.register(registry)`.

`KeyConfig.readKey` strips the `-----BEGIN/END-----` markers and all whitespace itself, so the full
PEM (headers included) is passed through unchanged.

### Proof the KeyConfig blocker is genuinely fixed

The KeyConfig failure was **latent, not the currently-observed** one — `flywayInitializer` aborts
the context refresh before Spring ever instantiates `jwtService` → `jwtPrivateKey`. So with Flyway
left enabled, the original file and the fixed file fail *identically*. To isolate KeyConfig I ran
both variants with `-Dspring.flyway.enabled=false` (a command-line override for diagnosis only; no
committed file was changed):

| Variant | Result |
|---|---|
| Original file, Flyway disabled | `Error creating bean with name 'jwtPrivateKey' ... class path resource [keys/private_key.pem] cannot be opened because it does not exist` |
| **This fix**, Flyway disabled | KeyConfig error **gone entirely** — zero mentions of `private_key`/`jwtPrivateKey` in the log; context proceeds past `securityConfig` → `jwtAuthFilter` → `jwtService` → `jwtPrivateKey`/`jwtPublicKey` and on to Spring AI's `vectorStore` |

## 4. Verification

### ShippingZoneServiceTest — 15 consecutive runs, all green

Requested 5-run tally:

| Run | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|
| Result | PASS | PASS | PASS | PASS | PASS |
| Totals | `Tests run: 9, Failures: 0, Errors: 0` on every run |

Extended by a further 10 runs to make the claim meaningful against a ~50% baseline failure rate:

| Run | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 | 15 |
|---|---|---|---|---|---|---|---|---|---|---|
| Result | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |

**15 / 15 PASS, 0 failures** (vs. 1 failure per ~2 runs before the fix). Zero
`PotentialStubbingProblem` occurrences anywhere in the full-suite log.

### Full suite

```
mise exec java@21.0.2 -- ./mvnw test
[ERROR] Tests run: 117, Failures: 0, Errors: 1, Skipped: 0
```

| | Before | After |
|---|---|---|
| Tests run | 101 | 117 |
| Failures | 1 (`ShippingZoneServiceTest$CityConflictValidation`) | **0** |
| Errors | 1 (`NocteonApiApplicationTests.contextLoads`) | 1 (same test, **different** cause — see §5) |

The +16 tests are **not** mine. They are the parallel agent's in-flight AI-module tests, which are
present as untracked files in this shared worktree: `ai/service/ProductDocumentFactoryTest.java`
(13 tests) + `ai/config/AiPropertiesTest.java` (3 tests) = exactly 16. My change adds one
non-test helper class (`TestJwtKeys`, no `@Test` methods, not picked up by surefire) and touches two
existing test classes without changing their test counts. So on a 101-test baseline the equivalent
comparison is **101 → 0 failures**, with the single `contextLoads` error remaining.

## 5. What `contextLoads` does now, and what still blocks it

**Not green, and I did not weaken it to make it so.** Precisely:

`contextLoads` now boots far enough to:

1. resolve the whole `application.yaml` config (including the `optional:file:.env` import — there
   is no `.env` in this worktree, and every value falls back to its default);
2. create the `DataSource` and **connect to PostgreSQL 16.15** at
   `jdbc:postgresql://localhost:5432/nocteon_db`;
3. — with Flyway disabled — build the JWT key beans and the entire Spring Security filter chain,
   which is the part this task fixed.

It then aborts on **Flyway migration validation**, not on connectivity and not on keys:

```
org.flywaydb.core.api.exception.FlywayValidateException: Validate failed: Migrations have failed validation
Detected resolved migration not applied to database: 10.
  at org.flywaydb.core.Flyway.migrate(Flyway.java:181)
  at org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer.afterPropertiesSet(...)
```

### Root cause of the remaining blocker — an environment/DB-state problem

`select version from flyway_schema_history order by installed_rank` on the local `nocteon_db`:

```
1..9, 100, 101, 102   (12 rows, all success=true)
```

`V10__enable_pgvector.sql` is **missing** from that history. It was added by commit `a800f84`
("chore(infra): add pgvector, Ollama, and Spring AI wiring") — i.e. the shared dev database was
migrated *before* this branch introduced V10. Because V100–V102 are already applied, Flyway sees V10
as an out-of-order pending migration and refuses to migrate rather than applying it.

And behind that sits a second, independent blocker. With Flyway disabled the run gets one step
further and fails in Spring AI instead:

```
Error creating bean with name 'vectorStore' (PgVectorStoreAutoConfiguration):
  SQL state [0A000]; ERROR: extension "vector" is not available
```

`select extname from pg_extension` on this instance returns only `plpgsql` — this PostgreSQL 16.15
has **no pgvector installed**, so neither V10 (`CREATE EXTENSION IF NOT EXISTS vector`) nor Spring
AI's `vectorStore` can succeed here regardless of test code.

### Connectivity is *not* the issue

The brief anticipated a datasource/Redis failure. That is not what is happening on this machine —
both services are already running natively (not via the Docker daemon, which was correctly left
alone):

- PostgreSQL 16.15 — TCP 5432 open, `postgres` processes present
- Redis — TCP 6379 open, `redis-server` present

### Why I did not fix this

Both remaining causes are outside the scope I was given, and fixing them by any in-scope route would
be wrong:

- `spring.flyway.out-of-order=true` would mean editing `src/main/resources/application.yaml` —
  explicitly off-limits, and a wrong default for production.
- Setting that property from inside `NocteonApiApplicationTests` is in-scope but is exactly the
  "weakening the test to pass" I was told not to do: it would make the integration test tolerate a
  permanently out-of-date dev schema and hide genuine migration breakage.
- Recreating or hand-patching `nocteon_db` is destructive to a database shared with the parallel
  agent, and `docker compose up` was ruled out.

The correct fix belongs to whoever owns this branch's migration story: run
`docker compose up -d` against the **pgvector** Postgres image from `docker-compose.yml` and
`flyway migrate` a fresh database (or `flyway repair` the existing one). `contextLoads` then needs
no further test changes.

## 6. Files changed

| File | Change |
|---|---|
| `src/test/java/com/nocteon/nocteon_api/shipping/service/ShippingZoneServiceTest.java` | `LinkedHashSet` + missing `"Cairo"` stub in one test; `LinkedHashSet` import |
| `src/test/java/com/nocteon/nocteon_api/NocteonApiApplicationTests.java` | `@DynamicPropertySource` supplying throwaway JWT keys |
| `src/test/java/com/nocteon/nocteon_api/support/TestJwtKeys.java` | **new** — runtime RSA-2048 generation + PEM wrapping |
| `src/test/resources/baseline-hygiene-report.md` | **new** — this report |

No `.pem` file was created or committed. Nothing outside the four paths above was touched; no file
listed as off-limits was modified.

## 7. Concerns

1. **`contextLoads` is still red**, on Flyway out-of-order validation of V10 plus a PostgreSQL
   without pgvector. That is an environment problem I could not fix in scope. Until it is resolved
   the suite has one permanent error, so "green baseline" is not yet achieved — the honest status is
   one green flake removed, one infrastructure blocker documented.
2. **The 117-test total includes the parallel agent's 16 untracked AI tests.** My before/after
   comparison is therefore not apples-to-apples on the raw count; the breakdown is in §4. Worth
   re-running once that agent's work lands to confirm a stable final total.
3. **`Set.of` is a latent flake factory in any test that stubs a per-element repository call.** This
   class is now clean, but the pattern (multi-element `Set.of` + one stub per element + strict
   stubs) will silently reintroduce coin-flip tests elsewhere. `LinkedHashSet` for any test whose
   iteration order affects assertions or stub consumption is the durable convention.
4. **`TestJwtKeys` generates a fresh keypair per JVM.** Any future test that signs a token in one
   test and verifies it in another is fine (same static pair), but a test that expects a *specific*
   signature will not be. That is the correct trade — determinism must not come from committed keys.