#!/usr/bin/env bash
set -euo pipefail

webhook_url="${DISCORD_WEBHOOK_URL:-}"
if [[ -z "$webhook_url" ]]; then
  exit 0
fi

status="${1:-FAILED}"
title="${2:-GitHub Actions workflow failed}"
detail="${3:-Inspect the workflow logs.}"
run_url="${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-TRI-READ/unknown}/actions/runs/${GITHUB_RUN_ID:-unknown}"
content="[TRI:READ][${status}]
${title}
${detail}
Repository: ${GITHUB_REPOSITORY:-unknown}
Workflow: ${GITHUB_WORKFLOW:-unknown}
Run: ${run_url}"

payload="$(jq -n --arg content "$content" '{content: $content}')"
curl --fail --silent --show-error \
  --header "Content-Type: application/json" \
  --data "$payload" \
  "$webhook_url" >/dev/null
