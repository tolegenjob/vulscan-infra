#!/usr/bin/env bash
# Общие функции для сценариев устойчивости R-1..R-4 (N-1). Source'ится, не выполняется напрямую.
set -euo pipefail

INFRA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$INFRA_DIR"
set -a
source .env
set +a

COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.lab.yml)
BASE="http://${FRONTEND_HOST_BIND:-127.0.0.1}:${FRONTEND_HOST_PORT:-8081}/api/v1"
JAR="$(mktemp)"
trap 'rm -f "$JAR"' EXIT

login() {
  local resp
  resp="$(curl -fsS -c "$JAR" -H 'Content-Type: application/json' \
    -d "{\"username\":\"${SCANNER_BOOTSTRAP_ADMIN_USER:-admin}\",\"password\":\"${SCANNER_BOOTSTRAP_ADMIN_PASSWORD}\"}" \
    "$BASE/auth/login")"
  CSRF="$(awk -F'\t' '$6=="XSRF-TOKEN"{print $7}' "$JAR")"
  [[ -n "$CSRF" ]] || { echo "login не вернул CSRF-токен: $resp" >&2; exit 1; }
}

api_post() {
  curl -fsS -b "$JAR" -c "$JAR" -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $CSRF" -d "$2" "$BASE$1"
}

api_get() { curl -fsS -b "$JAR" "$BASE$1"; }

# Создаёт approved target (lab-app) + endpoint, возвращает "targetId endpointId" через stdout.
setup_target_and_endpoint() {
  local url="$1" param="$2"
  local target endpoint target_id endpoint_id
  target="$(api_post /targets "{\"name\":\"resilience-$(date +%s)\",\"baseUrl\":\"http://lab-app:8080\"}")"
  target_id="$(echo "$target" | jq -r .id)"
  api_post "/targets/$target_id/verify" '{}' >/dev/null
  api_post "/targets/$target_id/approve" '{}' >/dev/null
  endpoint="$(api_post "/targets/$target_id/endpoints" "{\"method\":\"GET\",\"url\":\"$url\",\"parameters\":[{\"name\":\"$param\",\"location\":\"QUERY\",\"sampleValue\":\"probe\"}],\"idempotent\":true}")"
  endpoint_id="$(echo "$endpoint" | jq -r .id)"
  echo "$target_id $endpoint_id"
}

create_scan() {
  local target_id="$1" module="$2" endpoint_id="$3"
  api_post /scans "{\"targetId\":\"$target_id\",\"modules\":[\"$module\"],\"endpointIds\":[\"$endpoint_id\"]}" | jq -r .id
}

scan_status() { api_get "/scans/$1" | jq -r .status; }
scan_attempt_count() { api_get "/scans/$1" | jq -r ".jobs[0].attempt"; }

wait_for_terminal() {
  local scan_id="$1" timeout_sec="${2:-300}" deadline status
  deadline=$((SECONDS + timeout_sec))
  while :; do
    status="$(scan_status "$scan_id")"
    case "$status" in
      PENDING|RUNNING|CANCELLING) ;;
      *) echo "$status"; return 0 ;;
    esac
    (( SECONDS > deadline )) && { echo "TIMEOUT($status)"; return 1; }
    sleep 3
  done
}
