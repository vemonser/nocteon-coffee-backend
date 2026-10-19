# CHANGE-CONTRACT — Item #9: live-verify the remaining trash flows

Run: 20261019-trash-coverage
Base commit: 470023d0315f7841c28f6de53eb5d81d45bad2bb

## Why

Items #1 and #3 verified the Category and Product trash flows end-to-end. The other
11 controllers (BrewingMethod, CoffeeVariety, Farm, Journal, Origin, Pairing,
ProcessingMethod, PromoCode, RoastLevel, ShippingZone, TastingNote) were only verified
statically (route presence). Two specific runtime risks were flagged:

- Product: `(Boolean) row[2]` cast on `is_active`.
- Journal: `JournalPostStatus.valueOf((String) row[4])` on a native VARCHAR.

Both can only fail against a real JDBC row, which no mocked unit test can see.

## Scope

Live-verify representative trash flows end-to-end, plus a static route-presence scan.
No code change.

## Acceptance criteria

| ID | Criterion | How verified |
|---|---|---|
| N-01 | Given the 13 trash controllers, when inspected, then each exposes `/deleted`, `/{id}/restore` and `/{id}/permanent` | static scan |
| N-02 | Given a trashed journal post with status PUBLISHED, when the trash listing is called, then the row appears with `status = "PUBLISHED"` (enum cast succeeded) | live on `:8080` |
| N-03 | Given a trashed promo code, when the trash listing is called, then it appears and disappears correctly after restore | live on `:8080` |
| N-04 | Given a trashed shipping zone, when the trash listing is called, then it appears and disappears correctly after restore | live on `:8080` |
| N-05 | The remaining 8 entities (BrewingMethod, CoffeeVariety, Farm, Origin, Pairing, ProcessingMethod, RoastLevel, TastingNote) follow the identical pattern (same `SoftDeleteRowFolder.fold`, same native-query shape, same casts to `String`/`Instant`/`Number`/`Boolean`) — no enum casts, no risky casts | static scan |

## Affected areas / blast radius

None — verification only. No file changed.

## Follow-ups recorded, not done here

- **Test coverage gap.** The 555-test suite is unit tests with Mockito; trash listings
  use native queries that need a real DB. No integration test exists for any trash flow
  (the comment in `JournalService` referenced a non-existent
  `SoftDeleteRepositoryIntegrationTest`). A `@SpringBootTest` + Testcontainers (or the
  dev DB) suite covering at least Product and Journal trash flows would close this.
- **#2 regression guard.** `hibernate.query.fail_on_pagination_over_collection_fetch=true`
  (config, 1 line) + a `StatementInspector`-based test.
- **Cosmetic:** `totalElements` count query joins `product_pairings` (dead work).

## Rollback

Nothing to roll back.
