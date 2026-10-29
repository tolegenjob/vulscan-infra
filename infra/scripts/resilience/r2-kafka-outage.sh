#!/usr/bin/env bash
# R-2: остановка kafka на 60с во время создания скана -> после старта задания публикуются.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
source ./_lib.sh

login
read -r target_id endpoint_id <<< "$(setup_target_and_endpoint http://lab-app:8080/xss/reflect-text q)"

echo "Останавливаю kafka на 60с..."
(cd ../.. && docker compose -f docker-compose.yml -f docker-compose.lab.yml stop kafka)
scan_id="$(create_scan "$target_id" XSS "$endpoint_id")"
echo "scan=$scan_id создан при недоступной kafka (Core кладёт задание в outbox)"
sleep 60
echo "Поднимаю kafka обратно..."
(cd ../.. && docker compose -f docker-compose.yml -f docker-compose.lab.yml start kafka)

status="$(wait_for_terminal "$scan_id" 300)"
echo "Итог: status=$status"
if [[ "$status" == COMPLETED* ]]; then
  echo "R-2: OK (задание опубликовалось и выполнилось после восстановления kafka)"
else
  echo "R-2: ПРОВЕРИТЬ ВРУЧНУЮ — ожидался COMPLETED после восстановления kafka" >&2
fi
