# evaluation — оценка модулей по ground truth (план, E-1/E-2)

Эта директория — не отдельный репозиторий. По плану дипломной работы она должна была жить в
`vulscan-evaluation`, но создать новый репозиторий в этой сессии нельзя (`gh repo create` отвергается:
"sessions are bound to their configured repositories"). Так как `vulscan-infra` уже играет роль
монорепозитория для инфраструктуры и тестовых стендов (`infra/`, `testbeds/`), инструмент оценки
размещён здесь, в `vulscan-infra/evaluation/`, вместо отдельного репо. Это отклонение от плана, не
согласованное заранее явно — если нужен отдельный репозиторий, перенос инструмента в него не требует
изменений в коде, только копирования директории и обновления ссылок в CI других репозиториев (их нет).

## Состав

```
evaluation/
├── runner/            инструмент оценки (Kotlin, Gradle) — этот README его и описывает
├── scenarios/          *.yaml сценарии прогона (план, E-2) — xss.yaml, sqli.yaml, idor.yaml
├── negatives/          доп. "безопасные" эндпоинты для FP-проверки (план, E-2)
├── runs/               результаты прогонов (в .gitignore, кроме manifest.json каждого прогона)
└── RESULTS.md          итоговый отчёт для диплома (человеком написанный, план, E-2)
```

`scenarios/`, `negatives/` и `RESULTS.md` — следующий шаг (E-2), пока не созданы.

## runner — что это

Отдельный Gradle-проект (`application`-плагин, чистый Kotlin, без Spring Boot), который:

1. логинится в Core (`/api/v1/auth/login`, double-submit CSRF-cookie как у фронтенда);
2. по каждому `--scenario <file>.yaml` создаёт/верифицирует/подтверждает target в Core, регистрирует
   эндпоинты, затем `--repeats` раз создаёт скан нужного модуля и ждёт завершения;
3. сравнивает полученные findings с `ground-truth.yaml` (план, T-1) через нормализованный
   `CaseKey` (`"<METHOD> <path-template> <paramName|-> [ownerLabel->attackerLabel]"`);
4. считает TP/FP/FN/Precision/Recall/дубли (план, E-1.2/E-1.3) и пишет `report.csv` + `report.md`
   в `--out <dir>` (по умолчанию `evaluation/runs/<UTC-таймстамп>/`), плюс `manifest.json`
   (план, E-1.5: воспроизводимость — sha256 ground-truth, коммит runner'а, хост).

Зависит только от `io.vulscan:contracts` (перечисления `Verdict`/`ModuleType`/общий `Json`-маппер) и
`org.yaml:snakeyaml` — никакого Kafka/Spring, обычный `java.net.http.HttpClient`.

## Сборка и запуск

```bash
# из каталога vulscan-kit (сосед vulscan-infra):
./gradlew publishToMavenLocal -PkitVersion=0.5.0

cd evaluation/runner
./gradlew build

./gradlew run --args="\
  --base-url http://127.0.0.1:8081 \
  --admin-user admin --admin-password <пароль из SCANNER_BOOTSTRAP_ADMIN_PASSWORD> \
  --ground-truth ../../../testbeds/lab-app/ground-truth.yaml \
  --scenario ../scenarios/xss.yaml --scenario ../scenarios/sqli.yaml --scenario ../scenarios/idor.yaml \
  --repeats 5 --threshold LIKELY \
  --out ../runs/manual-$(date -u +%Y%m%d-%H%M%S)"
```

### Флаги CLI

| Флаг | Обязателен | По умолчанию | Смысл |
|---|---|---|---|
| `--base-url` | нет | `http://127.0.0.1:8081` | адрес Core |
| `--admin-user` | нет | `admin` | логин администратора Core |
| `--admin-password` | да (в живом режиме) | — | пароль администратора |
| `--ground-truth` | да | — | путь к `ground-truth.yaml` (план, T-1) |
| `--scenario` | да, можно повторять | — | путь к файлу сценария (план, E-2); минимум один |
| `--repeats` | нет | `5` | число повторов на сценарий (план, E-1.4 — проверка детерминизма) |
| `--threshold` | нет | `LIKELY` | минимальный `Verdict`, засчитываемый как позитив (`SUSPECTED`/`LIKELY`/`CONFIRMED`) |
| `--out` | нет | `evaluation/runs/<UTC-таймстамп>` | куда писать `manifest.json`/`raw/`/`report.csv`/`report.md` |
| `--repo-dir` | нет | `.` | откуда брать `git rev-parse HEAD` для манифеста (обычно корень vulscan-infra) |
| `--from-raw <dir>` | — | — | **отдельный режим**: пересчитать `report.csv`/`report.md` из уже сохранённых `<dir>/raw/*.json` и `<dir>/manifest.json`, без единого обращения к Core (план, E-1: критерий завершения — отчёт можно пересчитать не трогая живую систему) |

## Формат файла сценария (`evaluation/scenarios/<module>.yaml`, план E-2)

```yaml
module: XSS                       # XSS | SQLI | IDOR — передаётся в CreateScanRequest.modules
target:
  name: xss-eval                  # к имени runner добавляет -<timestamp>, чтобы повторные прогоны не конфликтовали
  baseUrl: http://lab-app:8080
endpoints:
  - method: GET
    url: http://lab-app:8080/xss/reflect-text
    idempotent: true
    parameters:
      - { name: q, location: QUERY, sampleValue: probe }
options: {}                        # прокидывается как доп. поля в CreateScanRequest (xss/sqli/idor), необязательно
```

## Формат `manifest.json` (план, E-1.5)

Содержит: UTC-таймстамп запуска, SHA коммита **и флаг "есть незакоммиченные изменения"** для
`vulscan-infra` (репозиторий, из которого запущен сам runner), версию lab-app (из `ground-truth.yaml`),
sha256 файла `ground-truth.yaml`, порог, число повторов, ОС/CPU/память хоста.

**Намеренное ограничение:** manifest фиксирует состояние только одного репозитория — того, что
содержит сам runner (`vulscan-infra`). Коммиты пяти остальных репозиториев (`vulscan-kit`,
`vulscan-core-backend`, `vulscan-xss-module`, `vulscan-sqli-module`, `vulscan-idor-module`) не
фиксируются автоматически, так как для них нет единого checkout — это нужно записывать вручную при
воспроизведении результатов (например, в `RESULTS.md`).

## Отчёт (`report.md`)

- Таблица "по модулям" — метрики **первого повтора** как представительные (намеренное упрощение,
  задокументировано в коде `ReportWriter.writeMarkdown`; полный анализ по всем повторам — в
  человеческом `RESULTS.md`, план E-2), плюс медиана и min–max длительности/числа запросов по всем
  повторам.
- Строка `micro-average, модули разного размера` (именно с такой подписью — план требует её явно)
  — суммы TP/FP/FN по модулям, не усреднение Precision/Recall по модулям — печатается только при
  нескольких сценариях за один запуск.
- Таблица "все повторы" — для проверки детерминизма; если TP/FP/FN отличаются между повторами одного
  модуля, под таблицей печатается предупреждение.

`report.csv` — та же таблица "все повторы" в плоском виде (`module,repeat,tp,fp,fn,duplicates,precision,recall,durationMs,requestsSent`) для вставки в Excel/Python при написании `RESULTS.md`.

## Известные ограничения

- `manifest.json` не фиксирует коммиты пяти других репозиториев (см. выше).
- Precision/Recall печатаются как `N/A`, когда знаменатель (`TP+FP` или `TP+FN`) равен нулю — это
  единственный случай `N/A`; `TP=0` при ненулевом знаменателе даёт `0.000`, а не `N/A` (план, E-1.3).
- Для IDOR `ownerLabel`/`attackerLabel` нужны для `CaseKey`, а список findings (`GET /findings`) их
  не отдаёт — для каждого IDOR-finding делается отдельный запрос `GET /findings/{id}`. В
  `--from-raw` режиме эти поля читаются из уже сохранённого сырого JSON (`raw/<module>-<repeat>.json`),
  без повторных обращений к Core.
- Нет локального Kotlin/Gradle-компилятора в среде разработки (эта сессия) — код проверялся только
  через CI GitHub Actions, без локальной компиляции.
