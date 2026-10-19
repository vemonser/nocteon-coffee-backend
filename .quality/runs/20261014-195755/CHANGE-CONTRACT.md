# Change Contract — Soft-delete trash bins for dashboard endpoints

Date: 2026-10-14
Risk: MEDIUM — adds 28 endpoints, changes no existing behaviour except one pre-existing
authorization gate (Category's trash bin).

## Scope

Every soft-deletable entity listed below gains two dashboard endpoints, following the pattern the
Category module already established:

- `GET /api/dashboard/{entity}/deleted` — list soft-deleted rows, paginated and searchable.
- `PATCH /api/dashboard/{entity}/{id}/restore` — clear `deleted_at` and bring the row back.

Entities: category, product, origin, farm, roast-level, processing-method, coffee-variety,
tasting-note, brewing-method, pairing, journal (post **and** category), promo-code, shipping-zone.
That is 14 entities / 28 endpoints.

## Non-goals

- **Order and Review are excluded.** Both extend `SoftDeletableEntity`, but an order is a financial
  record with payments attached and a review carries moderation state; un-deleting either needs
  rules this feature does not have. Decided with the user.
- **No hard delete.** Everything stays soft-deleted; nothing purges.
- **No new permissions.** Reuses the existing enum.
- **No change to the public storefront endpoints.**

## Design constraint that shapes everything

`SoftDeletableEntity` carries `@SQLRestriction("deleted_at IS NULL")`, so *every* JPQL and derived
query hides trashed rows. Listing them requires native SQL that bypasses the entity manager.
Because a native query also bypasses `LIMIT`/`OFFSET`, the listings page in memory: fetch all
trashed rows, sort, `subList`, wrap in `PageImpl`. That is a deliberate trade — acceptable for an
admin recovery screen whose trash is small, and the cost it incurs is the same one Category already
accepts.

## Acceptance criteria

- **AC-01** Given a trashed row exists, when `GET /dashboard/{entity}/deleted` is called, then the
  response contains that row.
- **AC-02** Given a trashed row, when `PATCH /dashboard/{entity}/{id}/restore` is called, then
  `deleted_at` is null and the row reappears in ordinary queries.
- **AC-03** Given the listings use native SQL, when each query executes against PostgreSQL, then it
  is valid SQL and returns the column width its mapper reads.
- **AC-04** Given no new permission is introduced, when any trash endpoint's `@PreAuthorize` string
  is resolved, then it matches a real `Permission` constant.
- **AC-05** Given `CUSTOMER` holds the catalog `*:read` permissions and `/dashboard/**` is merely
  `.authenticated()`, when a customer calls a trash endpoint, then access is denied.
- **AC-06** Given a trashed entity has N translations, when the listing is called, then the entity
  appears exactly once, carrying all N translations.
- **AC-07** Given a caller requests a page past the end of the result set, when the listing is
  called, then an empty page is returned rather than an error.
- **AC-08** Given Order and Review are out of scope, when their controllers are inspected, then
  they declare no trash endpoint.

## Two defects found and fixed during verification

Both were caught by tests written for this change, after an adversarial review:

1. **`JournalPostStatus` cast from a native query.** Native queries return the raw `VARCHAR`;
   Hibernate applies `@Enumerated` only to JPQL and derived queries. Casting `row[4]` straight to
   the enum threw `ClassCastException` on every trashed-post request — a 500 in production.
2. **One row per translation.** The native queries select the translation columns, so they return
   one row per language; the `GROUP BY` cannot collapse that because those columns are in both the
   select list and the grouping key. Mapping rows directly listed each trashed item once per
   translation and reported a row count as `totalElements`, which also split one item across two
   pages. Fixed by folding on id (`SoftDeleteRowFolder`).

## Security change

Trash bins were gated on `{entity}:read` — which `CUSTOMER` holds to browse the public catalog.
Combined with `/dashboard/**` being only `.authenticated()`, any registered customer could list
every trashed row, including journal posts whose status is `DRAFT` and which were never published.

Both endpoints are now gated on `{entity}:delete`, which no non-admin role holds. This also closes
an asymmetry: restore had been gated on `{entity}:update`, which `MODERATOR` holds, so a moderator
could un-trash content they were not allowed to delete.

## Known limitations (accepted, not fixed)

- **In-memory pagination** — described under "Design constraint" above.
- **Restore performs no referential-integrity check** — a farm can be restored while its origin
  stays trashed; a journal post while its category stays trashed. Also `ShippingZoneService.restore`
  does not re-run `validateNoCityConflict`, so two zones can end up claiming one city.
- **A trashed row with no translations never appears in the trash bin** and so cannot be restored
  through the API. Unreachable via the application, which requires translations on create.
- **A restored product does not re-enter the vector index** until the nightly rebuild;
  `ProductMutationService` schedules index removal on delete and nothing reverses it.