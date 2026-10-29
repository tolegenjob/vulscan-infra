# negatives/ — негативная цель "clean" (план, E-2.4)

Второй инстанс lab-app, профиль `clean` (`testbeds/lab-app/src/main/kotlin/io/vulscan/lab/clean/`):
те же маршруты, что у обычного lab-app, но без единой уязвимости соответствующего класса
(`CleanXssController`/`CleanSqliController`/`CleanIdorController` подменяют `XssLabController`/
`SqliLabController`/`IdorLabController` — у всех `@Profile`, конфликта маппингов нет).

## Поднять

```bash
docker compose -f infra/docker-compose.yml -f infra/docker-compose.lab.yml -f infra/docker-compose.lab-clean.yml up -d
```

(добавляет `lab-db-clean` + `lab-app-clean`, оба только на `lab-net`, рядом с обычными `lab-db`/`lab-app` —
`infra/scripts/up.sh` их не поднимает автоматически, это отдельный ручной шаг для E-2, не часть
обычной среды N-1; известное отклонение, см. `infra/README.md`).

## Запустить

```bash
cd evaluation/runner
./gradlew run --args="\
  --base-url http://127.0.0.1:8081 --admin-user admin --admin-password <...> \
  --ground-truth ../negatives/ground-truth-clean.yaml \
  --scenario ../negatives/xss-clean.yaml --scenario ../negatives/sqli-clean.yaml --scenario ../negatives/idor-clean.yaml \
  --repeats 5 --threshold LIKELY --out ../runs/negatives-\$(date -u +%Y%m%d-%H%M%S)"
```

## Ожидаемый результат (план, E-2.4б)

Все 16 случаев в `ground-truth-clean.yaml` — `expectedVulnerable: false`. Ожидается `TP=0, FP=0`,
`Precision=N/A` (знаменатель `TP+FP=0` — не 0.0 и не 1.0, см. `Metrics`/E-1.3). Результат
интерпретируется **по FP**, а не по Precision/Recall: любой `FP > 0` здесь — заведомая ложная
находка, которую стоит разобрать вручную (то же самое для `/safe/*`/`/fp/*` внутри основного
`ground-truth.yaml`, но этот файл фокусируется на целом приложении без уязвимостей, а не на
отдельных эндпоинтах рядом с уязвимыми).

## Ограничения этой негативной цели

- Повторяет только «уязвимые по форме» эндпоинты (XSS-01..05, SQLI-01..03, IDOR-01..08) — `/safe/*`
  и `/fp/*` здесь не продублированы: они не относятся к «той же уязвимости на безопасной цели»,
  а уже проверяются в основных `scenarios/*.yaml` на обычном lab-app.
- Для `/api/orders/{id}` и `/api/documents/{id}` с владельцем `admin` модуль классифицирует проверку
  как **вертикальную** (`RoleOrder`: роль `admin` выше `alice`/`bob`), хотя по сути это тот же
  горизонтальный «не проверили владельца» баг — несовпадение `vulnType` с намерением ground-truth.yaml
  не влияет на сопоставление (`CaseKey` не включает `vulnType`), но стоит иметь в виду при разборе.
- `CLEAN-IDOR-08` (`/api/admin/users`, параметра нет) использует технический `idParameter.name = "-"`
  как безвредный лишний query-параметр, чтобы `Core finding.parameterName` буквально совпал с
  литералом `-`, который использует `CaseKey`/ground-truth.yaml для «нет параметра» — не опечатка.
- Ни один сценарий здесь не прогонялся вживую (нет Docker в среде разработки этой сессии) — конфиг
  проверен только на соответствие контрактам Core/модулей по исходному коду, а не запуском.
