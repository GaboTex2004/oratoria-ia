#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
compose=(sudo docker compose -f docker-compose.yml -f docker-compose.deploy.yml)
"${compose[@]}" config --quiet
for service in backend ai-service frontend; do
    "${compose[@]}" build "$service"
done
if [[ "${1:-}" == "--bootstrap-admin" ]]; then
    sudo env APP_PROFILE=dev docker compose -f docker-compose.yml -f docker-compose.deploy.yml up -d --wait --wait-timeout 1800
fi
"${compose[@]}" up -d --wait --wait-timeout 1800
"${compose[@]}" exec -T frontend nginx -s reload
"${compose[@]}" exec -T ai-service python scripts/download_models.py --whisper base --pose --verify-only
"${compose[@]}" exec -T ollama ollama show qwen2.5:1.5b >/dev/null
curl --fail --silent --show-error http://127.0.0.1:15173/api/test
"${compose[@]}" ps
