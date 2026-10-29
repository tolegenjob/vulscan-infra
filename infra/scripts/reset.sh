#!/usr/bin/env bash
# Останавливает стек и удаляет тома (pgdata, kafka-data, labdbdata) — полный чистый старт.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
read -r -p "Это удалит все данные (pgdata, kafka-data, labdbdata). Продолжить? [y/N] " answer
case "$answer" in
  y|Y) ;;
  *) echo "Отменено."; exit 0 ;;
esac
docker compose -f docker-compose.yml -f docker-compose.lab.yml down -v
