# infra — Docker Compose окружение (N-1)

Поднимает весь стек одной командой: PostgreSQL, Kafka (KRaft, один узел), Core Backend,
три модуля (XSS/SQLi/IDOR), frontend, и — отдельным профилем — лабораторную цель `lab-app`
со своей БД (T-1).

## Требуемый layout каталогов

Репозитории — **соседние директории одного родителя** (не монорепозиторий):

```
<parent>/
  vulscan-kit/              # библиотека contracts/net/worker (publishToMavenLocal)
  vulscan-core-backend/
  vulscan-frontend/
  vulscan-xss-module/
  vulscan-sqli-module/
  vulscan-idor-module/
  vulscan-infra/            # этот репозиторий
    infra/                  # <- вы здесь
    testbeds/lab-app/       # T-1, лабораторная цель (часть этого репозитория)
```

Склонируйте все шесть репозиториев рядом друг с другом перед запуском.

## Быстрый старт

```bash
cd infra
cp .env.example .env
# заполнить секреты в .env — см. комментарии в файле (команды генерации указаны прямо там)
./scripts/up.sh      # публикует vulscan-kit в mavenLocal, собирает jar/дистрибутивы, docker compose build && up -d
./scripts/smoke.sh   # логин → target → verify → approve → endpoint → scan XSS → >= 1 finding
```

Frontend: `http://127.0.0.1:8081` (адрес/порт — `FRONTEND_HOST_BIND`/`FRONTEND_HOST_PORT` в `.env`).

Остановить: `./scripts/down.sh` (тома сохраняются).
Полный чистый старт: `./scripts/reset.sh` (удаляет `pgdata`, `kafka-data`, `labdbdata`).

## Сети и изоляция

| Сеть | Участники | Назначение |
|---|---|---|
| `db-net` | `postgres`, `core-backend` | БД Core; модули туда **не** подключены — доступа к БД нет |
| `bus-net` | `kafka`, `kafka-init`, `core-backend`, три модуля | обмен задания/результаты через Kafka |
| `lab-net` (`internal: true`) | `core-backend`, три модуля, `lab-app`, `lab-db` | изолированная лабораторная цель; наружу недоступна |
| `edge` | `frontend`, `core-backend` | единственная сеть, публикуемая на хост (через `frontend`) |

`core-backend` на `lab-net` — только для DNS-резолва и проверки владения (`TargetVerificationService`);
сам он ничего не сканирует. Подсеть `lab-net` задаётся один раз в `.env` (`LAB_SUBNET`) и используется
и в `ipam` (docker), и как `SCANNER_ALLOWED_PRIVATE_CIDRS`/`WORKER_ALLOWED_PRIVATE_CIDRS` — чтобы
allowlist не расходился со реальной подсетью (риск R-5 из плана).

Контейнеры `core-backend` и трёх модулей: непривилегированный пользователь (уже в их `Dockerfile`),
`read_only: true` + `tmpfs: /tmp`, `cap_drop: [ALL]`, `security_opt: no-new-privileges`, лимиты
`mem_limit`/`cpus` из `.env`.

## Профиль `lab`

`lab-app` + `lab-db` подключаются отдельным файлом (`-f docker-compose.lab.yml`, это уже делают все
скрипты). `lab-app` слушает только `lab-net` — порт на хост по умолчанию не публикуется.

**`lab-open` (ручная отладка):** чтобы временно открыть `lab-app` на `127.0.0.1`, добавьте ещё один
`-f` с мини-файлом вида:
```yaml
services:
  lab-app:
    ports: ["127.0.0.1:8082:8080"]
```
и не забудьте убрать его перед демонстрацией/оценкой — цель должна быть достижима только из
`lab-net`.

## Учётные данные `lab-app`

Пароли `alice`/`bob`/`admin` в `lab-app` фиксированы в `seed.sql` (намеренно — это упрощённый
учебный стенд, не прод-система, см. комментарий в `seed.sql`). Это отличается от первоначального
пункта плана про генерацию паролей скриптом `up.sh` в `infra/.lab-secrets.env` — генерировать
«случайные» пароли для статического seed-файла было бы фиктивным действием, поэтому этот пункт
не реализован буквально; фактические пароли см. в `testbeds/lab-app/src/main/resources/seed.sql`.

## Профиль `docker` у Core

Core не использует отдельный Spring-профиль `docker`: все нужные значения (allowlist, секреты,
БД, Kafka) уже читаются из переменных окружения в базовом `application.yml`, поэтому `.env` —
единственный источник конфигурации для compose-окружения (переменная `SPRING_PROFILES_ACTIVE=docker`
в `docker-compose.yml` зарезервирована на случай, если профиль понадобится позже — сейчас файла
`application-docker.yml` нет, и активация профиля без файла — no-op).

## Проверка владения (`verify`) для `lab-app`

`TargetVerificationService` (Core) подтверждает владение, запрашивая
`GET <baseUrl>/.well-known/vulnscan-<token>.txt` и ожидая тело = `<token>`. `lab-app` принадлежит
той же команде, что и сканер, поэтому этот файл отражается автоматически
(`WellKnownController`) — для стороннего, не принадлежащего команде приложения, владелец размещает
такой файл вручную.

## Скрипты

- `scripts/up.sh` — публикует `vulscan-kit` в `mavenLocal`, собирает `bootJar`/`installDist` во всех
  репозиториях, `docker compose build && up -d`, ждёт `core-backend` healthy.
- `scripts/down.sh` — останов без удаления томов.
- `scripts/reset.sh` — останов с удалением томов (просит подтверждения).
- `scripts/smoke.sh` — критерий завершения N-1 целиком: readiness → login → target → verify →
  approve → endpoint → scan XSS → `COMPLETED` ≤ 5 минут → ≥ 1 finding. Требует `jq`.
- `scripts/resilience/r1..r4-*.sh` — сценарии устойчивости (ниже). Запускаются вручную перед
  демонстрацией/оцениванием (E-3); не входят в CI.

## Сценарии устойчивости (R-1…R-4)

| Скрипт | Действие | Ожидаемый результат |
|---|---|---|
| `r1-module-restart.sh` | `docker compose restart xss-module` во время `RUNNING` | задание перезапускается (`attempt=2`), скан завершается |
| `r2-kafka-outage.sh` | останов `kafka` на 60с во время создания скана | после возврата `kafka` задание публикуется и выполняется |
| `r3-core-outage.sh` | останов `core-backend` на 30с во время скана | модуль переживает простой (retry heartbeat/результатов); после возврата Core обрабатывает результаты |
| `r4-lab-db-outage.sh` | останов `lab-db` | SQLi-проверки дают `inconclusive`, **не** findings (БД Core отдельная — логин/target не затронуты) |

Каждый скрипт печатает итоговый статус и свой вердикт (`OK` / «проверить вручную»); сравните с
колонкой «Ожидаемый результат» перед демонстрацией.

## Логи и миграции

`docker compose logs -f <service>`; ротация `json-file` (`LOG_MAX_SIZE`/`LOG_MAX_FILE` в `.env`).
Схему БД Core применяет Flyway при старте (единственный владелец схемы); `lab-app` — обычный
`spring.sql.init` (`schema.sql`/`seed.sql`), это тестовый стенд, не прод-БД.

## Известные отступления от первоначального плана

1. Нет файла `application-docker.yml` у Core — см. раздел «Профиль `docker` у Core» выше.
2. `lab-app`/`lab-db` не публикуются на хост по умолчанию, а `lab-open` — не отдельный
   compose-профиль, а задокументированный ручной оверлей (см. выше), чтобы не множить файлы.
3. Пароли `lab-app` статичны в `seed.sql`, не генерируются `up.sh` — см. раздел про учётные данные.
4. `core-backend` подключён к сети `edge` (не только к `lab-net`/`bus-net`/`db-net`, как в тексте
   плана) — без этого nginx (`frontend`) не смог бы резолвить `core-backend` для `proxy_pass`.
