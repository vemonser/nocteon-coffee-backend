#!/usr/bin/env bash
# ============================================================
# Rebuild the database from scratch and prove the chain applies.
# ============================================================
# The database is dropped, recreated and migrated with Flyway, and the
# applied versions are printed. A failure anywhere stops the script.
#
# Used as the evidence for the Flyway criterion: the whole chain has to
# apply to an EMPTY database, with no duplicate version and no manual
# repair in between.
set -euo pipefail

DB_NAME="${DB_NAME:-nocteon_db}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-postgres}"
export PGPASSWORD="${DB_PASSWORD:-postgres}"

echo "--- terminating the open sessions ---"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres \
  -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$DB_NAME';" || true

echo "--- dropping and recreating $DB_NAME ---"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -c "DROP DATABASE IF EXISTS $DB_NAME;"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -c "CREATE DATABASE $DB_NAME;"

echo "--- applying the migrations ---"
./mvnw flyway:migrate \
  -Dflyway.url="jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME" \
  -Dflyway.user="$DB_USER" \
  -Dflyway.password="$PGPASSWORD"

echo "--- the applied versions ---"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"

echo "--- the seeded orders ---"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
  -c "SELECT order_number, status, payment_status, payment_method, total_amount FROM orders ORDER BY id;"
