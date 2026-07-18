#!/usr/bin/env bash
set -euo pipefail

DEPLOY_DIR="${TRI_READ_DEPLOY_DIR:-$HOME/tri-read}"

cd "$DEPLOY_DIR"
test -f .env
test -f compose.yaml

exec docker compose --env-file .env -f compose.yaml exec -T db \
  sh -ec 'exec pg_dump \
    --username="$POSTGRES_USER" \
    --dbname="$POSTGRES_DB" \
    --format=custom \
    --compress=6 \
    --no-owner \
    --no-privileges'
