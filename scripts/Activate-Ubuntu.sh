#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
domain="$(python3 -c 'from pathlib import Path; print(dict(line.split("=", 1) for line in Path(".env").read_text().splitlines() if "=" in line)["PUBLIC_DOMAIN"])' )"
if [[ ! "$domain" =~ ^[a-z0-9][a-z0-9.-]*[a-z0-9]$ ]]; then
    printf '%s\n' 'Dominio inválido.' >&2
    exit 1
fi
sudo test -f "/etc/letsencrypt/live/$domain/fullchain.pem"
expected="$(curl --fail --silent --show-error http://127.0.0.1:15173/api/test)"
temporary="$(mktemp)"
backup="$(mktemp)"
trap 'rm -f "$temporary" "$backup"' EXIT
sed "s/__DOMAIN__/$domain/g" deploy/nginx/host.conf.template > "$temporary"
sudo cat /etc/nginx/sites-available/oratoria-ia > "$backup"
sudo install -m 644 deploy/nginx/proxy.conf /etc/nginx/snippets/oratoria-proxy.conf
sudo install -m 644 "$temporary" /etc/nginx/sites-available/oratoria-ia
if ! sudo nginx -t; then
    sudo install -m 644 "$backup" /etc/nginx/sites-available/oratoria-ia
    exit 1
fi
sudo systemctl reload nginx
for attempt in {1..10}; do
    if actual="$(curl --fail --silent --show-error "https://$domain/api/test")" && [[ "$actual" == "$expected" ]]; then
        printf '%s\n' "$actual"
        exit 0
    fi
    sleep 1
done
printf '%s\n' 'El dominio HTTPS no devuelve la respuesta del backend esperado.' >&2
exit 1
