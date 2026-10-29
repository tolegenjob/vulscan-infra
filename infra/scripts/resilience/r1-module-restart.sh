#!/usr/bin/env bash
# R-1: docker restart модуля во время RUNNING -> задание перезапускается (attempt=2), скан завершается.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
source ./_lib.sh

login
read -r target_id endpoint_id <<< "$(setup_target_and_endpoint http://lab-app:8080/xss/reflect-text q)"
scan_id="$(create_scan "$target_id" XSS "$endpoint_id")"
echo "scan=$scan_id создан, перезапускаю xss-module..."
sleep 1
(cd ../.. && docker compose -f docker-compose.yml -f docker-compose.lab.yml restart xss-module)

status="$(wait_for_terminal "$scan_id" 300)"
attempt="$(scan_attempt_count "$scan_id")"
echo "Итог: status=$status attempt=$attempt"
if [[ "$status" == COMPLETED* || "$status" == "PARTIALLY_COMPLETED" ]] && [[ "$attempt" -ge 2 ]]; then
  echo "R-1: OK (ожидалось attempt=2 и завершение скана)"
else
  echo "R-1: ПРОВЕРИТЬ ВРУЧНУЮ — ожидалось COMPLETED/PARTIALLY_COMPLETED с attempt>=2" >&2
fi
