#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-https://tri-read.duckdns.org}"
BASE_URL="${BASE_URL%/}"
RETRY_COUNT="${SMOKE_RETRY_COUNT:-36}"
RETRY_DELAY="${SMOKE_RETRY_DELAY:-5}"

# The 1 GB OCI instance can take a little over a minute to start the JVM.
CURL_RETRY_ARGS=(
  --retry "$RETRY_COUNT"
  --retry-delay "$RETRY_DELAY"
  --retry-all-errors
  --connect-timeout 10
)

curl --fail --silent --show-error \
  "${CURL_RETRY_ARGS[@]}" \
  "$BASE_URL/" \
  | grep -q '<title>TRI:READ</title>'

HEALTH_RESPONSE="$(curl --fail --silent --show-error \
  "${CURL_RETRY_ARGS[@]}" \
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
