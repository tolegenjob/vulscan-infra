#!/usr/bin/env bash
# N-1, критерий завершения: стек поднят → core-backend readiness=UP → логин bootstrap-админом →
# POST /targets на lab-app → verify → approve → создание endpoint → POST /scans (XSS) →
# ожидание терминального статуса ≤ 5 минут → >= 1 finding.
#
# Требует: jq, curl. Запускается после infra/scripts/up.sh (стек уже поднят, профиль lab включён).
set -euo pipefail

INFRA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$INFRA_DIR"
set -a
source .env
set +a

COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.lab.yml)
BASE="http://${FRONTEND_HOST_BIND:-127.0.0.1}:${FRONTEND_HOST_PORT:-8081}/api/v1"
JAR="$(mktemp)"
trap 'rm -f "$JAR"' EXIT

step() { echo; echo "=== $* ==="; }

step "1/7 core-backend readiness"
health=""
for _ in $(seq 1 30); do
  health="$("${COMPOSE[@]}" ps --format '{{.Name}} {{.Health}}' | awk '/core-backend/ {print $2}')"
  [[ "$health" == "healthy" ]] && break
  sleep 2
done
if [[ "$health" != "healthy" ]]; then
  echo "core-backend не стал healthy. docker compose ps:" >&2
  "${COMPOSE[@]}" ps >&2
  exit 1
fi
echo "OK: core-backend healthy"

step "2/7 login bootstrap-админом"
login_resp="$(curl -fsS -c "$JAR" -H 'Content-Type: application/json' \
  -d "{\"username\":\"${SCANNER_BOOTSTRAP_ADMIN_USER:-admin}\",\"password\":\"${SCANNER_BOOTSTRAP_ADMIN_PASSWORD}\"}" \
  "$BASE/auth/login")"
csrf="$(awk -F'\t' '$6=="XSRF-TOKEN"{print $7}' "$JAR")"
if [[ -z "$csrf" ]]; then
  echo "Не удалось получить CSRF-токен. Ответ логина: $login_resp" >&2
  exit 1
fi
echo "OK: вошли как $(echo "$login_resp" | jq -r .username)"

api_post() {
  local path="$1" body="$2"
  curl -fsS -b "$JAR" -c "$JAR" -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $csrf" -d "$body" "$BASE$path"
}

step "3/7 создание target (lab-app)"
target="$(api_post /targets '{"name":"smoke-lab-app","baseUrl":"http://lab-app:8080"}')"
target_id="$(echo "$target" | jq -r .id)"
echo "OK: target $target_id (status=$(echo "$target" | jq -r .status))"

step "4/7 verify + approve"
api_post "/targets/$target_id/verify" '{}' >/dev/null
approved="$(api_post "/targets/$target_id/approve" '{}')"
echo "OK: target status=$(echo "$approved" | jq -r .status)"

step "5/7 endpoint (GET /xss/reflect-text?q=)"
endpoint="$(api_post "/targets/$target_id/endpoints" '{
  "method": "GET",
  "url": "http://lab-app:8080/xss/reflect-text",
  "parameters": [{"name": "q", "location": "QUERY", "sampleValue": "smoke-probe"}],
  "idempotent": true
}')"
endpoint_id="$(echo "$endpoint" | jq -r .id)"
echo "OK: endpoint $endpoint_id"

step "6/7 scan (XSS)"
scan="$(api_post /scans "{\"targetId\":\"$target_id\",\"modules\":[\"XSS\"],\"endpointIds\":[\"$endpoint_id\"]}")"
scan_id="$(echo "$scan" | jq -r .id)"
echo "OK: scan $scan_id создан, ожидание терминального статуса (<= 5 минут)..."

deadline=$((SECONDS + 300))
status="PENDING"
while [[ "$status" == "PENDING" || "$status" == "RUNNING" || "$status" == "CANCELLING" ]]; do
  if (( SECONDS > deadline )); then
    echo "Таймаут: scan $scan_id не завершился за 5 минут (последний статус: $status)" >&2
    exit 1
  fi
  sleep 5
  scan="$(curl -fsS -b "$JAR" "$BASE/scans/$scan_id")"
  status="$(echo "$scan" | jq -r .status)"
done
echo "OK: scan завершён со статусом $status за $SECONDS с"

if [[ "$status" != "COMPLETED" ]]; then
  echo "Ожидался статус COMPLETED, получено: $status. Ответ: $scan" >&2
  exit 1
fi

step "7/7 findings"
findings="$(curl -fsS -b "$JAR" "$BASE/findings?scanId=$scan_id&size=100")"
count="$(echo "$findings" | jq '.items | length')"
echo "Найдено findings: $count"
if [[ "$count" -lt 1 ]]; then
  echo "Ожидался хотя бы 1 finding (эндпоинт /xss/reflect-text заведомо уязвим, ground-truth XSS-01)." >&2
  exit 1
fi

echo
echo "SMOKE OK: scan $scan_id, статус $status, findings=$count"
