#!/usr/bin/env bash
set -euo pipefail

docker exec nocteon-db pg_dump -U postgres -d nocteon_db \
  --schema-only --no-owner --no-privileges \
  --exclude-table=flyway_schema_history --exclude-table=vector_store \
  > /tmp/schema_old_check.sql
docker exec nocteon-db pg_dump -U postgres -d nocteon_flyway_check \
  --schema-only --no-owner --no-privileges \
  --exclude-table=flyway_schema_history --exclude-table=vector_store \
  > /tmp/schema_new_check.sql

sed -e '/^\\restrict/d' -e '/^\\unrestrict/d' /tmp/schema_old_check.sql > /tmp/schema_old_norm.sql
sed -e '/^\\restrict/d' -e '/^\\unrestrict/d' /tmp/schema_new_check.sql > /tmp/schema_new_norm.sql

diff -u /tmp/schema_old_norm.sql /tmp/schema_new_norm.sql
echo "schema-diff-ok"
