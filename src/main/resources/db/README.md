# Flyway layout

```
db/
  migration/   # schema + operational reference data (always applied)
  seed/        # demo catalog/orders/users/notifications (dev + test only)
```

## Rules

1. `db/migration/V1..V11` is the **one-time baseline** of the current schema.
   Every table's final definition lives in its domain file:
   - `V2__auth_tables.sql` — users, tokens, profiles, roles, permissions
   - `V3__catalog_tables.sql` — categories, lookups, products and product graph
   - `V4__commerce_tables.sql` — carts, orders, payments, promo codes, reviews
   - `V5__journal_tables.sql` — journal/content
   - `V6__notification_tables.sql` — notifications + email log
   - `V7__broadcast_contact_tables.sql` — broadcasts + contact messages
   - `V8__shipping_settings_tables.sql` — shipping zones + store settings
   - `V9__audit_tables.sql` — audit log + updated-at trigger
   - `V10__foreign_keys.sql` — all foreign keys, after every table exists
   - `V11__reference_data.sql` — roles/permissions, store settings, shipping zones
2. **Do not edit an applied version.** After this baseline is deployed, every
   schema change must be a new `V12+` migration, even when a table belongs to an
   older domain file. Flyway validates checksums; editing an applied file blocks
   startup.
3. `db/seed` holds demo data only. It is loaded by default locally and in CI.
   Production sets `SPRING_FLYWAY_LOCATIONS=classpath:db/migration`, so demo
   rows are never inserted there.
4. `vector_store` is intentionally not in `db/migration`: Spring AI owns its
   schema (`spring.ai.vectorstore.pgvector.initialize-schema=true`) and it is
   only created when the `vector` extension is available.

## Applying to a fresh database

```bash
# schema + demo data (local/dev)
./mvnw flyway:migrate \
  -Dflyway.url=jdbc:postgresql://localhost:5432/nocteon_db \
  -Dflyway.user=postgres -Dflyway.password=postgres \
  -Dflyway.locations=classpath:db/migration,classpath:db/seed

# or rebuild the whole local database (destructive). The default nocteon_db is
# protected by a guard; pass ALLOW_REAL_DB=1 only when you mean to wipe it.
DB_NAME=nocteon_rebuild ./scripts/rebuild-database.sh
ALLOW_REAL_DB=1 DB_NAME=nocteon_db ./scripts/rebuild-database.sh
```

The baseline replaces the old V1..V124 chain. An existing database that already
has the old `flyway_schema_history` must be recreated (the old versions are no
longer present locally); this project's data is disposable, so use the rebuild
script above. Never run this against a database whose data you need.
