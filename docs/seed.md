# Dev seed

Deterministic, re-runnable seed for local development. Destroys dev data.

## Warning
This script wipes the demo tables in the target database and re-inserts the
fixed dataset. Never run it against production. The default target is
`nocteon_db` on the local `nocteon-db` container.

## Backup first
```bash
docker exec nocteon-db pg_dump -U postgres -d nocteon_db > /tmp/opencode/seed-backup.sql
```

## Run
```bash
node scripts/seed-dev.mjs --yes
```
Refuses to run without `--yes`.

## Verify (checksum)
```bash
node scripts/seed-dev.mjs --verify
```
Prints per-table counts/sums and a combined SHA-256 checksum. Two runs should
produce the same checksum.

## Restore
```bash
docker exec -i nocteon-db psql -U postgres -d nocteon_db < /tmp/opencode/seed-backup.sql
```

## What it creates
Users/roles (admin + customers), categories, origins, farms, roast levels,
processing methods, coffee varieties, tasting notes, pairings, brewing methods,
product tags, shipping zones (reference), promo codes, ~7 products with
variants and images, reviews (approved/pending), ~42 orders across statuses
with items and payments, contact messages, notifications, inventory movements.

## Invariants
- Relative timestamps: `date_trunc('day', now()) - N days`, never future.
- Order numbers increase with `created_at`.
- Every order has at least one line item.
- Idempotent: same ids and order numbers on every run.
NODE