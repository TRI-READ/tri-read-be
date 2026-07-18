#!/usr/bin/env bash
set -euo pipefail

DOMAIN="${1:-tri-read}"
ENV_FILE="/etc/tri-read-duckdns.env"

sudo install -d -m 0755 /usr/local/lib/tri-read

sudo tee /usr/local/lib/tri-read/update-duckdns.sh >/dev/null <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

: "${DUCKDNS_DOMAIN:?DUCKDNS_DOMAIN is required}"
: "${DUCKDNS_TOKEN:?DUCKDNS_TOKEN is required}"

response="$(curl -fsS --get 'https://www.duckdns.org/update' \
  --data-urlencode "domains=${DUCKDNS_DOMAIN}" \
  --data-urlencode "token=${DUCKDNS_TOKEN}" \
  --data-urlencode 'ip=')"

if [[ "${response}" != "OK" ]]; then
  echo "DuckDNS update failed: ${response}" >&2
  exit 1
fi

echo "DuckDNS update succeeded for ${DUCKDNS_DOMAIN}.duckdns.org"
EOF
sudo chmod 0755 /usr/local/lib/tri-read/update-duckdns.sh

if [[ ! -f "${ENV_FILE}" ]]; then
  printf 'DUCKDNS_DOMAIN=%s\nDUCKDNS_TOKEN=\n' "${DOMAIN}" | sudo tee "${ENV_FILE}" >/dev/null
fi
sudo chmod 0600 "${ENV_FILE}"

sudo tee /etc/systemd/system/tri-read-duckdns.service >/dev/null <<'EOF'
[Unit]
Description=Update TRI:READ DuckDNS record
After=network-online.target
Wants=network-online.target

[Service]
Type=oneshot
EnvironmentFile=/etc/tri-read-duckdns.env
ExecStart=/usr/local/lib/tri-read/update-duckdns.sh
NoNewPrivileges=true
PrivateTmp=true
ProtectHome=true
ProtectSystem=strict
EOF

sudo tee /etc/systemd/system/tri-read-duckdns.timer >/dev/null <<'EOF'
[Unit]
Description=Update TRI:READ DuckDNS record every five minutes

[Timer]
OnCalendar=*-*-* *:0/5:00
Persistent=true
RandomizedDelaySec=20
Unit=tri-read-duckdns.service

[Install]
WantedBy=timers.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now tri-read-duckdns.timer

echo "DuckDNS updater installed. Add the token to ${ENV_FILE}, then start tri-read-duckdns.service."
