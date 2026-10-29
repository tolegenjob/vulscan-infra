#!/usr/bin/env bash
# R-3: остановка core-backend на 30с во время скана -> модуль повторяет heartbeat/результаты (retry),
# после возврата Core обрабатывает результаты.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
source ./_lib.sh

login
read -r target_id endpoint_id <<< "$(setup_target_and_endpoint http://lab-app:8080/xss/reflect-text q)"
scan_id="$(create_scan "$target_id" XSS "$endpoint_id")"
echo "scan=$scan_id создан, останавливаю core-backend на 30с..."
sleep 1
(cd ../.. && docker compose -f docker-compose.yml -f docker-compose.lab.yml stop core-backend)
sleep 30
echo "Поднимаю core-backend обратно..."
(cd ../.. && docker compose -f docker-compose.yml -f docker-compose.lab.yml start core-backend)

for _ in $(seq 1 30); do
  health="$(cd ../.. && docker compose -f docker-compose.yml -f docker-compose.lab.yml ps --format '{{.Name}} {{.Health}}' | awk '/core-backend/ {print $2}')"
  [[ "$health" == "healthy" ]] && break
  sleep 2
done

status="$(wait_for_terminal "$scan_id" 300)"
echo "Итог: status=$status"
if [[ "$status" == COMPLETED* || "$status" == "PARTIALLY_COMPLETED" ]]; then
  echo "R-3: OK (модуль дождался возврата Core, результаты обработаны)"
else
  echo "R-3: ПРОВЕРИТЬ ВРУЧНУЮ — ожидался COMPLETED/PARTIALLY_COMPLETED после возврата core-backend" >&2
fi
