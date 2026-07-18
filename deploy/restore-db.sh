#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: BACKUP_ENCRYPTION_KEY=... ./restore-db.sh <backup.dump.enc> --confirm

This command replaces the current TRI:READ database. It creates a local
pre-restore safety dump, stops the application, restores the selected backup,
and starts the application again.
EOF
}

if [ "$#" -ne 2 ] || [ "$2" != "--confirm" ]; then
  usage >&2
  exit 2
fi

ENCRYPTED_BACKUP="$1"
DEPLOY_DIR="${TRI_READ_DEPLOY_DIR:-$HOME/tri-read}"

test -s "$ENCRYPTED_BACKUP"
: "${BACKUP_ENCRYPTION_KEY:?BACKUP_ENCRYPTION_KEY is required}"

cd "$DEPLOY_DIR"
test -f .env
test -f compose.yaml

mkdir -p backups
chmod 700 backups

SAFETY_DUMP="backups/pre-restore-$(date -u +%Y%m%dT%H%M%SZ).dump"
TEMP_DUMP="$(mktemp --suffix=.dump)"
APP_STOPPED=false

cleanup() {
  rm -f "$TEMP_DUMP"
  if [ "$APP_STOPPED" = true ]; then
    docker compose --env-file .env -f compose.yaml start app >/dev/null
  fi
}
trap cleanup EXIT

openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 \
  -pass env:BACKUP_ENCRYPTION_KEY \
  -in "$ENCRYPTED_BACKUP" \
  -out "$TEMP_DUMP"

docker compose --env-file .env -f compose.yaml exec -T db \
  pg_restore --list < "$TEMP_DUMP" >/dev/null

./backup-db.sh > "$SAFETY_DUMP"
chmod 600 "$SAFETY_DUMP"

docker compose --env-file .env -f compose.yaml stop app
APP_STOPPED=true

docker compose --env-file .env -f compose.yaml exec -T db sh -ec '
  dropdb --username="$POSTGRES_USER" --force "$POSTGRES_DB"
  createdb --username="$POSTGRES_USER" "$POSTGRES_DB"
  pg_restore \
    --username="$POSTGRES_USER" \
    --dbname="$POSTGRES_DB" \
    --no-owner \
    --no-privileges \
    --exit-on-error
' < "$TEMP_DUMP"

docker compose --env-file .env -f compose.yaml start app
APP_STOPPED=false

echo "Database restored. Safety dump: $DEPLOY_DIR/$SAFETY_DUMP"
