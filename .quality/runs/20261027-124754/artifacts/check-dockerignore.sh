#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../../.."

grep -q '^\.quality/$' .dockerignore
grep -q '^artifacts/$' .dockerignore
echo "dockerignore-ok"
