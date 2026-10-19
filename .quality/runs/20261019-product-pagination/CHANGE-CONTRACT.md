# CHANGE-CONTRACT — Item #2: is `ProductRepository.findAllDashboard` paginating in memory?

Run: 20261019-product-pagination
Base commit: 470023d0315f7841c28f6de53eb5d81d45bad2bb

## The claim under test

Recorded during the stash recovery as a **HIGH** risk, from a static read of the recovered code:

> `findAllDashboard` (``ProductRepository.java:89-107``) carries
> `LEFT JOIN FETCH p.pairings` — a to-many — alongside a `Pageable`, so Hibernate
> cannot apply `LIMIT`/`OFFSET` in SQL and falls back to loading the whole table and
> slicing it in the JVM (`HHH90003004`).

## Verdict: the claim does not reproduce. No code change is made.

Live evidence, against the rebuilt container on `:8080` (Hibernate ORM **7.4.1.Final**,
Spring Boot 4.1), with SQL logging on (`[org.hibernate.SQL]: DEBUG`):

```sql
select p1_0.id, ..., p2_0.product_id, p2_1.id, ...
from (
    select distinct p1_0.id, p1_0.category_id, ...
    from products p1_0
    left join product_translations t1_0 on p1_0.id = t1_0.product_id
    left join categories c1_0 on c1_0.id = p1_0.category_id and (c1_0.deleted_at IS NULL)
    where (p1_0.deleted_at IS NULL) and (...) ...
    order by p1_0.created_at desc
    offset ? rows fetch first ? rows only          <-- the limit is in the database
) p1_0(...)
left join product_pairings p2_0 on p1_0.id = p2_0.product_id
left join pairings p2_1 on p2_1.id = p2_0.pairing_id
order by p1_0.created_at desc
```

Hibernate 7 solves the collection-fetch + `Pageable` conflict by putting the paginated
`DISTINCT` projection in a **derived table** and joining the collection *outside* it. The
collection-fetch row multiplication therefore happens only over the already-limited page,
not over the catalog. `HHH90003004` appears **0** times in the entire container log,
including at startup and across all probe traffic.

This matters because the failure would be invisible in the response body: in-memory
pagination still returns the *correct* page, it only reads the whole table. The SQL is the
only observable that separates the two, which is why the criteria assert on the statement
rather than on the payload.

### Why the earlier static read was reasonable, and why it is now wrong

Before Hibernate 6.2 a collection fetch join with `setMaxResults` did silently paginate in
memory; the warning id dates from that behaviour. The recovered stash was written against
the same ORM as the running build, so the risk was never confirmed against a runtime — it
was inferred from the query text. It is now refuted against the runtime.

**Caveat recorded honestly:** the conclusion is bound to ORM **7.4.1.Final**. A downgrade
across the Hibernate 6.1/6.2 boundary would reintroduce the old fallback. Nothing in the
project pins Hibernate directly (`spring-boot-starter-data-jpa` resolves it), so a future
Spring Boot downgrade is the trigger to re-check.

## Scope

Verify or refute the claim. If refuted, change nothing and record the evidence.

## Non-goals

- No refactor, no "defensive" rewrite of `findAllDashboard`.
- No change to the fetch-join strategy: it is deliberate (see the javadoc on
  `findAllActiveForAiProjection`, `ProductRepository.java:120-140`, which explains the
  one-to-many-fetch rule and its measured statement counts).
- No audit of the other 13 `findAllDashboard` variants beyond a structural scan.

## Acceptance criteria

| ID | Criterion | How verified |
|---|---|---|
| PP-01 | Given the dashboard product listing is requested with a page size smaller than the catalog, when the issued SQL is inspected, then the row limit is applied by the database and no in-memory pagination warning is logged | SQL text on `:8080` + `HHH90003004` count over the whole log |
| PP-02 | Given any page, when the pages are walked, then their union is exactly the catalog with no duplicate and no skipped row | page walk at `size=3` compared with an unpaged read |
| PP-03 | Given the listing is filtered (category slug, search, active), when it is paged, then the limit is still applied by the database | SQL text per filter |

## Affected areas / blast radius

**None — no file is modified by this run.** The change under test is unverified rather than
broken, so the outcome is evidence, not code.

Risk of the *conclusion* being wrong: mitigated by asserting on the SQL statement instead of
the response, and by testing page 0 **and** page 1 (a non-zero `OFFSET` is where in-memory
pagination would show up).

## Structural scan (why this is the only such place)

Of every `JOIN FETCH` in `src/main/java`, only two sit on a method returning `Page<>`:

| Query | Fetched association | Cardinality | Verdict |
|---|---|---|---|
| `ProductRepository.findAllDashboard` | `p.pairings` | **to-many** | verified above |
| `ProductBrewingMethodRepository.findByBrewingMethodSlug` | `pbm.product` | to-one | cannot break pagination |

`JournalPostRepository.findAllDashboard` fetches `p.author`, which is also to-one. So
`p.pairings` is the only to-many fetch in the project that meets a `Pageable`.

## Validation plan

1. `python3 artifacts/assert-pp.py PP-01` — SQL shape, warning count, page 0 and page 1.
2. `python3 artifacts/assert-pp.py PP-02` — exact page walking.
3. `python3 artifacts/assert-pp.py PP-03` — limit preserved under filters.
4. Evidence Gate over `evidence-manifest.json`.

## Follow-ups recorded, not done here

1. **No regression guard exists.** Nothing in the test suite would notice if Hibernate
   started paginating this query in memory. A repository test asserting the JDBC row count
   via Hibernate `Statistics` (page rows × pairings, not catalog rows × pairings) would
   catch it. Classified as test work, not a defect.
2. The `totalElements` count query joins `product_pairings` even though it counts
   `DISTINCT p1_0.id`; the join cannot change the count, so it is dead work of negligible
   cost on this schema. Cosmetic.
3. `page=1&size=5` returns 2 rows with `totalElements=7` — the `size=5` cap is honoured and
   `last=true`; no off-by-one at the boundary.

## Rollback

Nothing to roll back: no file changed. The run's artifacts are additive.
