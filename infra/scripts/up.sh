#!/usr/bin/env bash
# N-1: собирает артefacts всех сервисов из соседних репозиториев и поднимает весь стек
# (включая профиль lab) одной командой. См. infra/README.md для требуемого layout каталогов.
set -euo pipefail

INFRA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PARENT_DIR="$(cd "$INFRA_DIR/../.." && pwd)"
cd "$INFRA_DIR"

if [[ ! -f .env ]]; then
  echo "infra/.env не найден. Скопируйте infra/.env.example в infra/.env и заполните секреты (см. комментарии в файле)." >&2
  exit 1
fi
set -a
source .env
set +a

COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.lab.yml)

gradle_build() {
  local repo="$1" task="$2"
  local dir="$PARENT_DIR/$repo"
  if [[ ! -d "$dir" ]]; then
    echo "Репозиторий $repo не найден по пути $dir (см. требуемый layout в infra/README.md)." >&2
    exit 1
  fi
  echo "==> $repo: ./gradlew $task"
  (cd "$dir" && ./gradlew "$task" -x test --quiet)
}

echo "=== 1/3: публикация vulscan-kit в mavenLocal ==="
gradle_build vulscan-kit publishToMavenLocal

echo "=== 2/3: сборка артефактов сервисов ==="
gradle_build vulscan-core-backend bootJar
gradle_build vulscan-xss-module installDist
gradle_build vulscan-sqli-module installDist
gradle_build vulscan-idor-module installDist
(cd "$INFRA_DIR/../testbeds/lab-app" && ./gradlew bootJar -x test --quiet)

echo "=== 3/3: docker compose build && up -d ==="
"${COMPOSE[@]}" build
"${COMPOSE[@]}" up -d

echo "Ожидание готовности core-backend..."
for _ in $(seq 1 60); do
  status="$("${COMPOSE[@]}" ps --format '{{.Name}} {{.Health}}' 2>/dev/null | awk '/core-backend/ {print $2}')"
  if [[ "$status" == "healthy" ]]; then
    break
  fi
  sleep 5
done

"${COMPOSE[@]}" ps

cat <<EOF

Готово. Frontend:        http://${FRONTEND_HOST_BIND:-127.0.0.1}:${FRONTEND_HOST_PORT:-8081}
Bootstrap admin:         ${SCANNER_BOOTSTRAP_ADMIN_USER:-admin} / (пароль из infra/.env)
Смоук-тест:              infra/scripts/smoke.sh
Остановить:              infra/scripts/down.sh
Остановить и стереть БД: infra/scripts/reset.sh
EOF
