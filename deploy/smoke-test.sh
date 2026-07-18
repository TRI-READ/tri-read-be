#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-https://tri-read.duckdns.org}"
BASE_URL="${BASE_URL%/}"

curl --fail --silent --show-error \
  --retry 12 --retry-delay 5 --retry-all-errors \
  "$BASE_URL/" \
  | grep -q '<title>TRI:READ</title>'

HEALTH_RESPONSE="$(curl --fail --silent --show-error \
  --retry 12 --retry-delay 5 --retry-all-errors \
  "$BASE_URL/api/health")"

printf '%s' "$HEALTH_RESPONSE" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"ok"'

echo "Smoke test passed: $BASE_URL"
