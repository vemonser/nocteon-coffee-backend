#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../../.."

REDIS_CONFIG=src/main/java/com/nocteon/nocteon_api/common/redis/RedisConfig.java
RATE_LIMITER=src/main/java/com/nocteon/nocteon_api/common/ratelimit/RateLimiterService.java

grep -q 'REDIS_URL' "$REDIS_CONFIG"
grep -q 'withSsl(redisSsl)' "$REDIS_CONFIG"
grep -q 'withTimeout(redisTimeout)' "$REDIS_CONFIG"
grep -q 'ping()' "$REDIS_CONFIG"
grep -q 'destroyMethod = "shutdown"' "$REDIS_CONFIG"
grep -q 'destroyMethod = "close"' "$REDIS_CONFIG"
grep -q 'StatefulRedisConnection<String, byte\[\]> connection' "$RATE_LIMITER"
grep -q 'LettuceConnectionFactory' "$REDIS_CONFIG"
grep -q 'springDataRedisUrl' "$REDIS_CONFIG"
grep -q 'Spring Data Redis startup PING succeeded' "$REDIS_CONFIG"
grep -q 'timeout: ${REDIS_TIMEOUT:5s}' src/main/resources/application.yaml
grep -q 'REDIS_SSL' readme.md
grep -q 'hard startup dependency' readme.md

echo "redis-config-ok"
