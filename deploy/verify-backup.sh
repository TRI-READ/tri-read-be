#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "Usage: BACKUP_ENCRYPTION_KEY=... ./verify-backup.sh <backup.dump.enc>" >&2
  exit 2
fi

ENCRYPTED_BACKUP="$1"
: "${BACKUP_ENCRYPTION_KEY:?BACKUP_ENCRYPTION_KEY is required}"
test -s "$ENCRYPTED_BACKUP"

RUN_SUFFIX="${GITHUB_RUN_ID:-local}-$(date -u +%s)-$$"
CONTAINER="tri-read-backup-verify-$RUN_SUFFIX"
VERIFY_DUMP="$(mktemp --suffix=.dump)"

cleanup() {
  docker rm --force "$CONTAINER" >/dev/null 2>&1 || true
  shred -u "$VERIFY_DUMP" 2>/dev/null || rm -f "$VERIFY_DUMP"
}
trap cleanup EXIT

openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 \
  -pass env:BACKUP_ENCRYPTION_KEY \
  -in "$ENCRYPTED_BACKUP" \
  -out "$VERIFY_DUMP"

docker run --detach --name "$CONTAINER" \
  --env POSTGRES_DB=tri_read_verify \
  --env POSTGRES_USER=tri_read_verify \
  --env POSTGRES_PASSWORD=tri_read_verify \
  postgres:18-bookworm >/dev/null

for attempt in $(seq 1 30); do
  if docker exec "$CONTAINER" \
    pg_isready --username=tri_read_verify --dbname=tri_read_verify >/dev/null; then
    break
  fi
  if [ "$attempt" = "30" ]; then
    echo "Temporary PostgreSQL did not become ready." >&2
    exit 1
  fi
  sleep 2
done

docker exec --interactive "$CONTAINER" pg_restore \
  --username=tri_read_verify \
  --dbname=tri_read_verify \
  --no-owner \
  --no-privileges \
  --exit-on-error < "$VERIFY_DUMP"

REQUIRED_TABLES="$(docker exec "$CONTAINER" psql \
  --username=tri_read_verify \
  --dbname=tri_read_verify \
  --tuples-only \
  --no-align \
  --set ON_ERROR_STOP=1 \
  --command "SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tablename IN ('flyway_schema_history', 'app_users', 'quiz_sets', 'passages', 'quiz_attempts', 'prompt_templates');")"
test "$REQUIRED_TABLES" = "6"

echo "Encrypted backup restored successfully in isolated PostgreSQL."
