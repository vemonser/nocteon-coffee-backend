# Change Contract — Permanent delete + Cloudinary deletes only on permanent delete

Date: 2026-10-15
Risk: MEDIUM — 14 new endpoints; changes soft-delete behaviour for 5 image-carrying entities
(removes Cloudinary deletion from the soft path).

## User requirement

Images must stay on Cloudinary while an item is only soft-deleted, so restore brings the item
back with its picture. Cloudinary deletion happens only on permanent delete.

## Scope

1. Removed `helper.deleteMediaIfExists(...)` from the soft `delete()` of: category, origin, farm,
   pairing, journal post. The image URL stays on the row; restore returns it intact. Image
   replacement on update/upload is untouched (replacing an image still deletes the old one).
2. New `DELETE /api/dashboard/{entity}/{id}/permanent` (gated `{entity}:delete`) for all 14
   trash-bin entities. Trash-only: reads through `findDeletedById`, so a live or unknown id is a
   not-found. Deletes Cloudinary assets first, then hard-deletes the row (translations cascade).
   Product additionally deletes every media asset; journal category, promo-code and shipping-zone
   have no media and just delete the row.
3. `AuthorizationConfigTest` trash guard extended to `/permanent` paths.

## Non-goals

- Order and Review: untouched, still no trash bin.
- Journal categories still have no admin create/update/delete (seed-only); their permanent-delete
  endpoint exists for symmetry with their restore endpoint.
- No referential pre-checks: permanently deleting a category/origin/farm still referenced by
  products fails at the database and surfaces as 409 via the existing
  `DataIntegrityViolationException` handler.

## Acceptance criteria

- **AC-01** Given an item with an image, when it is soft-deleted, then the image URL stays on the
  row and Cloudinary is never called.
- **AC-02** Given a restored item, when it is read back, then it carries the same image URL it had
  before deletion.
- **AC-03** Given a trashed item, when permanent-delete is called, then its row is gone from the
  database and its Cloudinary assets were deleted in the same operation.
- **AC-04** Given a live or unknown id, when permanent-delete is called, then it fails with
  not-found and deletes nothing.
- **AC-05** Given the trash endpoints, when their `@PreAuthorize` strings are resolved, then every
  one (including `/permanent`) is gated on a permission CUSTOMER does not hold.
- **AC-06** Given a hard delete refused by a foreign key, when the 409 is rendered, then the
  message says the item is still in use rather than claiming a duplicate.
