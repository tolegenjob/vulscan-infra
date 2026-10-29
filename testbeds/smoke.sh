#!/usr/bin/env bash
# testbeds/smoke.sh — ручная дымовая проверка lab-app (T-1): поднимает контейнер с Postgres рядом
# (если ещё не запущен) и прогоняет несколько ключевых запросов curl-ом, печатая статус/тело ответа.
# Не заменяет ground-truth.yaml/GroundTruthConsistencyTest — только быстрая проверка "приложение живо".
set -euo pipefail

BASE_URL="${LAB_APP_BASE_URL:-http://localhost:8080}"
COOKIE_JAR="$(mktemp)"
trap 'rm -f "$COOKIE_JAR"' EXIT

pass=0
fail=0

check() {
    local description="$1"
    local expected_status="$2"
    shift 2
    local actual_status
    actual_status="$(curl -s -o /tmp/smoke-body.$$ -w '%{http_code}' "$@")" || actual_status="curl-error"
    if [[ "$actual_status" == "$expected_status" ]]; then
        echo "OK   [$actual_status] $description"
        pass=$((pass + 1))
    else
        echo "FAIL [$actual_status, ожидалось $expected_status] $description"
        echo "     тело ответа: $(cat /tmp/smoke-body.$$ 2>/dev/null | head -c 300)"
        fail=$((fail + 1))
    fi
    rm -f /tmp/smoke-body.$$
}

echo "== lab-app smoke test against $BASE_URL =="
echo

echo "-- health --"
check "actuator/health отвечает 200" 200 "$BASE_URL/actuator/health"

echo
echo "-- auth --"
check "логин alice успешен" 200 -c "$COOKIE_JAR" -X POST "$BASE_URL/login" \
    --data-urlencode "username=alice" --data-urlencode "password=alice-pass-1"
check "логин с неверным паролем -> 401" 401 -X POST "$BASE_URL/login" \
    --data-urlencode "username=alice" --data-urlencode "password=wrong"
check "/api/me без сессии -> 401" 401 "$BASE_URL/api/me"
check "/api/me с валидной сессией -> 200" 200 -b "$COOKIE_JAR" "$BASE_URL/api/me"

echo
echo "-- xss (reflected, без аутентификации) --"
check "reflect-text отражает q" 200 "$BASE_URL/xss/reflect-text?q=%3Cscript%3E"
check "safe/encoded-text кодирует q" 200 "$BASE_URL/safe/xss/encoded-text?q=%3Cscript%3E"

echo
echo "-- sqli (без аутентификации) --"
check "error-based возвращает ответ на корректный id" 200 "$BASE_URL/sqli/error/products?id=1"
check "безопасный двойник products принимает id" 200 "$BASE_URL/safe/sqli/products?id=1"

echo
echo "-- idor (alice авторизована, читает заказ bob) --"
check "уязвимый /api/orders/{id} отдаёт чужой заказ (1002, bob)" 200 -b "$COOKIE_JAR" "$BASE_URL/api/orders/1002"
check "безопасный /api/safe/orders/{id} отказывает на чужой заказ -> 404" 404 -b "$COOKIE_JAR" "$BASE_URL/api/safe/orders/1002"
check "публичный пост доступен без сессии" 200 "$BASE_URL/api/posts/3001"

echo
echo "== итог: $pass успешно, $fail провалено =="
[[ "$fail" -eq 0 ]]
