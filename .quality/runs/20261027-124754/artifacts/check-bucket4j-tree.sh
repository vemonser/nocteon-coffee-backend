#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../../.."

out="$(./mvnw -o dependency:tree -Dincludes=com.bucket4j 2>&1)"

echo "$out" | grep -q 'com.bucket4j:bucket4j-redis:jar:8.10.1'
echo "$out" | grep -q 'com.bucket4j:bucket4j-core:jar:8.10.1'
if echo "$out" | grep -q 'bucket4j_jdk17-core'; then
  echo "duplicate bucket4j_jdk17-core is still present"
  exit 1
fi

# Exactly the two expected artifact lines, no third Bucket4j jar.
count="$(echo "$out" | grep -c 'com.bucket4j:' || true)"
test "$count" -eq 2
echo "bucket4j-tree-ok"
