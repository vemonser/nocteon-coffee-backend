#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../../.."

HANDLER=src/main/java/com/nocteon/nocteon_api/auth/oauth2/OAuth2SuccessHandler.java
CONTROLLER=src/main/java/com/nocteon/nocteon_api/auth/controller/AuthController.java

grep -q 'oauth2/callback?code=' "$HANDLER"
if grep -q 'oauth2/callback?token=' "$HANDLER"; then
  echo "access token is still placed in the redirect query"
  exit 1
fi
grep -q '/oauth2/exchange' "$CONTROLLER"
grep -q 'exchangeOAuth2Code' "$CONTROLLER"
echo "oauth2-one-time-code-ok"
