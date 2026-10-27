#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")/../../../.."

set +e
SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/nocteon_flyway_check' \
  REDIS_PORT=6399 REDIS_URL= SPRING_DATA_REDIS_URL= \
  mise exec java@25 -- ./mvnw -o -Dtest=NocteonApiApplicationTests test \
  > /tmp/redis-failfast-check.log 2>&1
status=$?
set -e

if [ "$status" -eq 0 ]; then
  echo "expected the context to fail, but it started"
  exit 1
fi

grep -q "Redis is unreachable" /tmp/redis-failfast-check.log
echo "redis-failfast-ok"
