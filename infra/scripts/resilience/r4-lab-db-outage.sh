#!/usr/bin/env bash
# R-4: остановка lab-db -> SQLi-проверки дают inconclusive, не findings (а не ложные срабатывания).
# core-backend использует свою отдельную БД ("postgres"), поэтому логин/создание target не затронуты.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
source ./_lib.sh

login
read -r target_id endpoint_id <<< "$(setup_target_and_endpoint http://lab-app:8080/sqli/error/products id)"

echo "Останавливаю lab-db..."
(cd ../.. && docker compose -f docker-compose.yml -f docker-compose.lab.yml stop lab-db)

scan_id="$(create_scan "$target_id" SQLI "$endpoint_id")"
status="$(wait_for_terminal "$scan_id" 300)"
findings_count="$(api_get "/findings?scanId=$scan_id&size=100" | jq '.items | length')"

echo "Поднимаю lab-db обратно..."
(cd ../.. && docker compose -f docker-compose.yml -f docker-compose.lab.yml start lab-db)

echo "Итог: status=$status findings=$findings_count"
if [[ "$findings_count" -eq 0 ]]; then
  echo "R-4: OK (при недоступной lab-db findings не появились — нет ложных срабатываний)"
else
  echo "R-4: ПРОВЕРИТЬ ВРУЧНУЮ — при недоступной lab-db появились findings ($findings_count), это неожиданно" >&2
fi
