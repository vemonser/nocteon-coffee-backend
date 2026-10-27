#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../../.."

java_all="$(docker exec nocteon-app java -version 2>&1)"
java_line="$(printf '%s\n' "$java_all" | grep 'openjdk version' || true)"
echo "$java_line"
[[ "$java_line" == *25* ]]

test "$(docker inspect -f '{{.State.Health.Status}}' nocteon-app)" = "healthy"
echo "app-health=healthy"
test "$(docker exec nocteon-app id -u)" = "10001"
echo "user=10001"

curl -fsS -m 10 -o /dev/null http://localhost:8080/api/settings
echo "settings=200"
curl -fsS -m 10 -o /dev/null http://localhost:8080/actuator/health/readiness
echo "readiness=200"

REDIS_PASSWORD="$(sed -n 's/^REDIS_PASSWORD=//p' .env | head -1)"
if [ -n "$REDIS_PASSWORD" ]; then
  reply="$(docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" nocteon-redis redis-cli ping)"
else
  reply="$(docker exec nocteon-redis redis-cli ping)"
fi
echo "redis=$reply"
test "$reply" = "PONG"

spring_data_pings="$(docker logs nocteon-app 2>&1 | grep -c 'Spring Data Redis startup PING succeeded' || true)"
bucket4j_pings="$(docker logs nocteon-app 2>&1 | grep -c 'Redis startup PING succeeded' || true)"
test "$spring_data_pings" -ge 1
test "$bucket4j_pings" -ge 1
echo "startup=redis-ping-ok"
