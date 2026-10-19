# CHANGE-CONTRACT — Items #6, #7, #8

Run: 20261019-messages-security-cleanup
Base commit: 470023d0315f7841c28f6de53eb5d81d45bad2bb

## Why

Three small, independent defects recorded in the post-recovery queue:

- **#6** `RoastLevelNotFoundException` and `PairingNotFoundException` report the wrong
  entity in their message (`error.farm.notFound` and `error.brewingMethod.notFound`).
- **#7** `/api/dashboard/users` is gated on `user:read`, which `CUSTOMER` holds (because
  `AddressController` reuses `user:read`/`user:update` for the self-service address book).
  Every customer can therefore list all users.
- **#8** `FarmService` imports `EntityInUseException` twice, and a `JournalService`
  comment references a test (`SoftDeleteRepositoryIntegrationTest`) that does not exist.

## Scope

- **#6** Point the two exceptions at the correct message keys (`error.roastLevel.notFound`,
  `error.pairing.notFound`), which already exist in both `messages.properties` and
  `messages_ar.properties`.
- **#7** Split the address permission: add `ADDRESS_READ`/`ADDRESS_UPDATE`, move them to
  `CUSTOMER` in place of `USER_READ`/`USER_UPDATE`, and re-gate `AddressController` on
  them. `AdminUserController` stays on `user:read`/`user:update` (admin/moderator only).
- **#8** Remove the duplicate import and rewrite the stale comment.

## Non-goals

- No change to the AdminUserController permission (stays `user:read`/`user:update`).
- No change to `MODERATOR` (keeps `USER_READ`/`USER_UPDATE` for dashboard user management).
- No new address endpoints or DTO changes.

## Acceptance criteria

| ID | Criterion | How verified |
|---|---|---|
| M-01 | Given a missing roast level / pairing, when requested on the dashboard, then the message is "Roast profile not found" / "Pairing not found" (not "Farm"/"Brewing method") | live on `:8080` |
| S-01 | Given a CUSTOMER token, `/api/addresses` answers 200 and `/api/dashboard/users` answers 403; given an ADMIN token, `/api/dashboard/users` answers 200 | live on `:8080` |
| C-01 | Given the codebase, `FarmService` imports `EntityInUseException` once and `JournalService` no longer references `SoftDeleteRepositoryIntegrationTest` | static scan |
| B-01 | Given the changes, `./mvnw compile` and `./mvnw test` pass | build logs |

## Affected areas / blast radius

| Area | Effect |
|---|---|
| `Permission` | +2 constants (`ADDRESS_READ`, `ADDRESS_UPDATE`) |
| `Role` | CUSTOMER swaps `USER_READ/UPDATE` → `ADDRESS_READ/UPDATE` |
| `AddressController` | 4 `@PreAuthorize` switched to `address:read`/`address:update` |
| 2 exception classes | message key corrected |
| `FarmService`, `JournalService` | duplicate import removed; comment corrected |

Risk: **LOW-MEDIUM** (permission re-grant; addresses must keep working for customers).
Blast radius: address endpoints + dashboard users endpoint.

## Validation plan

1. `python3 artifacts/assert-msc.py M-01 S-01 C-01`
2. `./mvnw compile` (exit 0) and `./mvnw test` (555 tests, 0 failures)
3. Evidence Gate over `evidence-manifest.json`

## Rollback

Revert the Permission/Role/AddressController changes together (they form one unit);
the message-key and import/comment fixes are independent.
