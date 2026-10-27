#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../../.."

OAUTH=src/main/java/com/nocteon/nocteon_api/auth/oauth2/CookieOAuth2AuthorizationRequestRepository.java

if grep -q 'new ObjectInputStream\|\.readObject()' "$OAUTH"; then
  echo "Java deserialization still present in the OAuth2 repository"
  exit 1
fi

grep -q 'StringRedisTemplate' "$OAUTH"
grep -q 'ResponseCookie' "$OAUTH"
grep -q 'SameSite=Lax\|sameSite("Lax")' "$OAUTH"
echo "oauth2-no-deserialization-ok"
