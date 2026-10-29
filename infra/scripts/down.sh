#!/usr/bin/env bash
# Останавливает стек без удаления томов (БД и данные Kafka переживают restart).
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
docker compose -f docker-compose.yml -f docker-compose.lab.yml down
