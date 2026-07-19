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

HEADERS="$(curl --fail --silent --show-error --head "$BASE_URL/")"
printf '%s\n' "$HEADERS" | grep -Eqi '^strict-transport-security:[[:space:]]*max-age=31536000'
printf '%s\n' "$HEADERS" | grep -Eqi '^x-content-type-options:[[:space:]]*nosniff'
printf '%s\n' "$HEADERS" | grep -Eqi '^x-frame-options:[[:space:]]*DENY'
printf '%s\n' "$HEADERS" | grep -Eqi '^referrer-policy:[[:space:]]*strict-origin-when-cross-origin'

AUTH_STATUS="$(curl --silent --output /dev/null --write-out '%{http_code}' "$BASE_URL/api/auth/me")"
test "$AUTH_STATUS" = "401"

echo "Smoke test passed: $BASE_URL"
