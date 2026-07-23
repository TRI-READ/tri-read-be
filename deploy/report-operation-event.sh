#!/usr/bin/env bash
set -euo pipefail

DEPLOY_DIR="${TRI_READ_DEPLOY_DIR:-$HOME/tri-read}"
EVENT_TYPE="${1:-}"
STATUS="${2:-}"
MESSAGE="${3:-}"

case "$EVENT_TYPE" in
  DB_BACKUP|APP_DEPLOY) ;;
  *) echo "Unsupported event type" >&2; exit 2 ;;
esac

case "$STATUS" in
  SUCCESS|FAILED) ;;
  *) echo "Unsupported event status" >&2; exit 2 ;;
esac

MESSAGE="${MESSAGE:0:500}"
MESSAGE="${MESSAGE//\'/\'\'}"

cd "$DEPLOY_DIR"
test -f .env
test -f compose.yaml

docker compose --env-file .env -f compose.yaml exec -T db sh -ec \
  "psql --username=\"\$POSTGRES_USER\" --dbname=\"\$POSTGRES_DB\" \
    --set ON_ERROR_STOP=1 \
    --command=\"INSERT INTO operation_events
      (event_type, status, message, completed_at)
      VALUES ('$EVENT_TYPE', '$STATUS', '$MESSAGE', CURRENT_TIMESTAMP);\""
