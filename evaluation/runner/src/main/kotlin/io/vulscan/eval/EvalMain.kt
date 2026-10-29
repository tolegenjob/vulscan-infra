package io.vulscan.eval

import tools.jackson.databind.JsonNode
import io.vulscan.kit.contracts.Json
import io.vulscan.kit.contracts.Verdict
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.time.format.DateTimeFormatter

/**
 * Один сценарий запуска (`evaluation/scenarios/<module>.yaml`, план E-2): какой target/эндпоинты
 * создать в Core и каким модулем сканировать. Формат:
 * ```yaml
 * module: XSS
 * target: { name: "xss-eval", baseUrl: "http://lab-app:8080" }
 * endpoints:
 *   - { method: GET, url: "http://lab-app:8080/xss/reflect-text", idempotent: true,
 *       parameters: [ { name: q, location: QUERY, sampleValue: probe } ] }
 * options: {}   # прокидывается как доп. поля CreateScanRequest (xss/sqli), необязательно
 * ```
 * Для IDOR нужны ещё `identities` и `resources` (создаются в Core до скана, `idor.identityIds`
 * в `options` собирается автоматически из всех созданных identities — см. `evaluation/scenarios/idor.yaml`):
 * ```yaml
 * module: IDOR
 * target: { name: "idor-eval", baseUrl: "http://lab-app:8080" }
 * endpoints:
 *   - { method: GET, url: "http://lab-app:8080/api/orders/{id}", idempotent: true,
 *       parameters: [ { name: id, location: PATH, sampleValue: "1001" } ] }
 * identities:
 *   - { label: alice, role: USER, loginUrl: "http://lab-app:8080/login", username: alice, password: alice-pass-1 }
 * resources:
 *   - { endpoint: "GET http://lab-app:8080/api/orders/{id}", idParameter: { name: id, location: PATH },
 *       idValue: "1001", owner: alice, sensitivity: HIGH, ownerMarkers: ["$.content"],
 *       access: [ { identity: bob, expected: DENY } ] }
 * ```
 */
data class Scenario(
    val module: String,
    val targetName: String,
    val targetBaseUrl: String,
    val endpoints: List<Map<String, Any?>>,
    val identities: List<Map<String, Any?>>,
    val resources: List<Map<String, Any?>>,
    val options: Map<String, Any?>,
) {
    companion object {
        fun load(file: File): Scenario {
            val root = file.inputStream().use {
                @Suppress("UNCHECKED_CAST")
                Yaml().load(it) as Map<String, Any?>
            }
            @Suppress("UNCHECKED_CAST")
            val target = root["target"] as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val endpoints = root["endpoints"] as List<Map<String, Any?>>
            @Suppress("UNCHECKED_CAST")
            val identities = (root["identities"] as? List<Map<String, Any?>>) ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val resources = (root["resources"] as? List<Map<String, Any?>>) ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val options = (root["options"] as? Map<String, Any?>) ?: emptyMap()
            return Scenario(root["module"] as String, target["name"] as String, target["baseUrl"] as String, endpoints, identities, resources, options)
        }
    }
}

/** "GET http://lab-app:8080/api/orders/{id}" -> ("GET", "http://lab-app:8080/api/orders/{id}"), для ключа `resources[].endpoint`. */
private fun endpointKeyOf(method: Any?, url: Any?): String = "${(method as String).uppercase()} $url"

/**
 * `options.xss.observeEndpoints` (список ключей "METHOD url", как в `resources[].endpoint`) — заменяется
 * на `options.xss.observeEndpointIds` (реальные UUID), требуемые `CreateScanRequest.xss` для stored XSS
 * (XssOptions.stored=true требует observeEndpointIds — XssJobValidator). Сценарий не может знать
 * UUID заранее, поэтому ссылается на эндпоинт по тому же ключу, что и везде в формате сценария.
 */
private fun resolveObserveEndpoints(options: Map<String, Any?>, endpointIdByKey: Map<String, String>): Map<String, Any?> {
    @Suppress("UNCHECKED_CAST")
    val xss = options["xss"] as? Map<String, Any?> ?: return options
    @Suppress("UNCHECKED_CAST")
    val observeKeys = xss["observeEndpoints"] as? List<String> ?: return options
    val ids = observeKeys.map { key -> endpointIdByKey[key] ?: error("options.xss.observeEndpoints: эндпоинт не найден — '$key'") }
    val resolvedXss = (xss - "observeEndpoints") + mapOf("observeEndpointIds" to ids)
    return options + mapOf("xss" to resolvedXss)
}

private class CliArgs(args: Array<String>) {
    private val map: MutableMap<String, MutableList<String>> = mutableMapOf()
    init {
        var i = 0
        while (i < args.size) {
            require(args[i].startsWith("--")) { "Неожиданный аргумент: ${args[i]}" }
            val key = args[i].removePrefix("--")
            val value = args.getOrNull(i + 1) ?: error("Для --$key не указано значение")
            map.getOrPut(key) { mutableListOf() } += value
            i += 2
        }
    }

    fun one(key: String, default: String? = null): String = map[key]?.first() ?: default ?: error("Требуется --$key")
    fun oneOrNull(key: String): String? = map[key]?.first()
    fun all(key: String): List<String> = map[key] ?: emptyList()
}

fun main(args: Array<String>) {
    val cli = CliArgs(args)
    val fromRaw = cli.oneOrNull("from-raw")
    if (fromRaw != null) {
        reportFromRaw(File(fromRaw), File(cli.one("ground-truth")), Verdict.valueOf(cli.one("threshold", "LIKELY")))
        return
    }

    val baseUrl = cli.one("base-url", "http://127.0.0.1:8081")
    val adminUser = cli.one("admin-user", "admin")
    val adminPassword = cli.one("admin-password")
    val repeats = cli.one("repeats", "5").toInt()
    val threshold = Verdict.valueOf(cli.one("threshold", "LIKELY"))
    val groundTruthFile = File(cli.one("ground-truth"))
    val scenarioFiles = cli.all("scenario").map(::File)
    require(scenarioFiles.isNotEmpty()) { "Нужен хотя бы один --scenario <путь к yaml>" }

    val outDir = File(cli.oneOrNull("out") ?: "evaluation/runs/${DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC))}")
    val rawDir = File(outDir, "raw").apply { mkdirs() }

    val groundTruth = GroundTruth.load(groundTruthFile)
    val manifest = RunManifest.capture(groundTruthFile, threshold.name, repeats, File(cli.oneOrNull("repo-dir") ?: ".")).copy(labAppVersion = groundTruth.labAppVersion)
    File(outDir, "manifest.json").writeText(Json.write(manifest))

    val client = CoreApiClient(baseUrl)
    client.login(adminUser, adminPassword)

    val reports = mutableListOf<ModuleReport>()
    for (scenarioFile in scenarioFiles) {
        val scenario = Scenario.load(scenarioFile)
        // Имя файла сценария (без расширения), а не scenario.module — так второй сценарий того же
        // модуля (например, sqli-timebased.yaml — план E-2: time-based считается отдельной строкой,
        // "нагрузка отличается") не перезатирает raw-файлы первого и не сливается с ним в отчёте.
        val label = scenarioFile.nameWithoutExtension
        println("=== $label (${scenario.module}): подготовка target/эндпоинтов ===")
        val targetId = client.createTarget("${scenario.targetName}-${System.currentTimeMillis()}", scenario.targetBaseUrl).get("id").asText()
        client.verifyTarget(targetId)
        client.approveTarget(targetId)
        val endpointIdByKey = LinkedHashMap<String, String>()
        scenario.endpoints.forEach { ep ->
            @Suppress("UNCHECKED_CAST")
            val parameters = (ep["parameters"] as? List<Map<String, Any?>>) ?: emptyList()
            val id = client.createEndpoint(targetId, (ep["method"] as String), ep["url"] as String, parameters, (ep["idempotent"] as? Boolean) ?: false)
                .get("id").asText()
            endpointIdByKey[endpointKeyOf(ep["method"], ep["url"])] = id
        }
        val endpointIds = endpointIdByKey.values.toList()
        val resolvedOptions = resolveObserveEndpoints(scenario.options, endpointIdByKey)

        // IDOR: identities + resources/access-rules создаются в Core ДО скана (план, E-1/I-1..I-4);
        // CreateScanRequest.idor несёт только identityIds — сами resources Core подбирает из уже
        // созданных access-rules таргета. Для XSS/SQLi этот блок — no-op (identities/resources пустые).
        val scanOptions: Map<String, Any?> = if (scenario.identities.isNotEmpty()) {
            val identityIdByLabel = scenario.identities.associate { idy ->
                val identityLabel = idy["label"] as String
                val id = client.createIdentity(
                    targetId, identityLabel, idy["role"] as String, "FORM_LOGIN",
                    mapOf("loginUrl" to (idy["loginUrl"] as String)),
                    mapOf("username" to (idy["username"] as String), "password" to (idy["password"] as String)),
                ).get("id").asText()
                identityLabel to id
            }
            scenario.resources.forEach { res ->
                @Suppress("UNCHECKED_CAST")
                val idParameter = res["idParameter"] as Map<String, Any?>
                val endpointId = endpointIdByKey[res["endpoint"] as String]
                    ?: error("resources: эндпоинт не найден среди endpoints — '${res["endpoint"]}'")
                val ownerIdentityId = identityIdByLabel[res["owner"] as String]
                    ?: error("resources: identity '${res["owner"]}' не описана в identities")
                @Suppress("UNCHECKED_CAST")
                val ownerMarkers = (res["ownerMarkers"] as? List<String>) ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val access = (res["access"] as List<Map<String, Any?>>).map { rule ->
                    val identityId = identityIdByLabel[rule["identity"] as String]
                        ?: error("resources.access: identity '${rule["identity"]}' не описана в identities")
                    mapOf("identityId" to identityId, "expected" to rule["expected"])
                }
                client.createAccessRule(
                    targetId, endpointId, idParameter, res["idValue"] as String, ownerIdentityId,
                    (res["sensitivity"] as? String) ?: "MEDIUM", ownerMarkers, access,
                )
            }
            resolvedOptions + mapOf("idor" to mapOf("identityIds" to identityIdByLabel.values.toList()))
        } else {
            resolvedOptions
        }

        val cases = groundTruth.casesFor(scenario.module)
        val repeatResults = mutableListOf<RepeatResult>()
        for (repeat in 1..repeats) {
            println("--- $label: повтор $repeat/$repeats ---")
            val started = System.currentTimeMillis()
            val scanId = client.createScan(targetId, listOf(scenario.module), endpointIds, scanOptions).get("id").asText()
            val scan = pollUntilTerminal(client, scanId)
            val finishedMs = System.currentTimeMillis() - started
            val findings = client.listAllFindings(scanId)
            saveRaw(File(rawDir, "${label}__${scenario.module}-$repeat.json"), scanId, scan, findings)

            val evalFindings = toEvalFindings(client, scenario.module, findings)
            val requestsSent = scan.get("jobs")?.firstOrNull()?.get("requestsSent")?.asInt() ?: 0
            repeatResults += RepeatResult(repeat, Metrics.of(Matcher.match(cases, evalFindings, threshold)), finishedMs, requestsSent)
        }
        reports += ModuleReport(label, repeatResults)
    }

    ReportWriter.writeCsv(File(outDir, "report.csv"), reports)
    ReportWriter.writeMarkdown(File(outDir, "report.md"), reports, manifest)
    println("Готово: $outDir/report.md, $outDir/report.csv")
}

/** `--from-raw`: пересчитывает метрики из уже сохранённых findings (план, E-1, критерий завершения) — без обращения к Core. */
private fun reportFromRaw(runDir: File, groundTruthFile: File, threshold: Verdict) {
    val groundTruth = GroundTruth.load(groundTruthFile)
    val manifestFile = File(runDir, "manifest.json")
    val manifest = if (manifestFile.exists()) Json.read(manifestFile.readText(), RunManifest::class.java) else error("Не найден $manifestFile")

    val rawFiles = File(runDir, "raw").listFiles { f -> f.extension == "json" }?.sortedBy { it.name } ?: emptyList()
    require(rawFiles.isNotEmpty()) { "Нет сырых findings в $runDir/raw" }

    // Имя файла: "<label>__<MODULE>-<repeat>.json" (см. main() выше) — группируем по label (строка
    // отчёта), а module (для фильтра ground-truth) берём из той же части имени, не из label.
    val byLabel = rawFiles.groupBy { it.name.substringBefore("__") }
    val reports = byLabel.map { (label, files) ->
        val module = files.first().name.substringAfter("__").substringBeforeLast('-')
        val cases = groundTruth.casesFor(module)
        val repeats = files.mapIndexed { index, file ->
            val raw = Json.mapper.readTree(file)
            val scan = raw.get("scan")
            val findingsRaw = raw.get("findings")
            val evalFindings = findingsRaw.toList().map { node ->
                EvalFinding(
                    id = node.get("id").asText(),
                    method = node.get("method").asText(),
                    url = node.get("url").asText(),
                    parameterName = node.get("parameterName")?.takeUnless { it.isNull }?.asText(),
                    verdict = Verdict.valueOf(node.get("verdict").asText()),
                    ownerLabel = node.get("details")?.get("ownerLabel")?.takeUnless { it.isNull }?.asText(),
                    attackerLabel = node.get("details")?.get("attackerLabel")?.takeUnless { it.isNull }?.asText(),
                )
            }
            val requestsSent = scan.get("jobs")?.firstOrNull()?.get("requestsSent")?.asInt() ?: 0
            RepeatResult(index + 1, Metrics.of(Matcher.match(cases, evalFindings, threshold)), 0, requestsSent)
        }
        ModuleReport(label, repeats)
    }

    ReportWriter.writeCsv(File(runDir, "report.csv"), reports)
    ReportWriter.writeMarkdown(File(runDir, "report.md"), reports, manifest.copy(threshold = threshold.name))
    println("Готово (из сырых findings): $runDir/report.md, $runDir/report.csv")
}

private fun saveRaw(file: File, scanId: String, scan: JsonNode, findings: List<JsonNode>) {
    val root = Json.mapper.createObjectNode()
    root.put("scanId", scanId)
    root.set("scan", scan)
    val findingsArray = Json.mapper.createArrayNode()
    findings.forEach { findingsArray.add(it) }
    root.set("findings", findingsArray)
    file.writeText(Json.mapper.writeValueAsString(root))
}

private fun pollUntilTerminal(client: CoreApiClient, scanId: String, timeoutMs: Long = 300_000): JsonNode {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (true) {
        val scan = client.getScan(scanId)
        val status = scan.get("status").asText()
        if (status !in setOf("PENDING", "RUNNING", "CANCELLING")) return scan
        if (System.currentTimeMillis() > deadline) error("scan $scanId не завершился за ${timeoutMs}мс (статус $status)")
        Thread.sleep(3000)
    }
}

/** Для IDOR нужны ownerLabel/attackerLabel из деталей — список findings их не содержит, дотягиваем точечно. */
private fun toEvalFindings(client: CoreApiClient, module: String, findings: List<JsonNode>): List<EvalFinding> {
    if (!module.equals("IDOR", ignoreCase = true)) {
        return findings.map { EvalFinding.fromListItem(it) }
    }
    return findings.map { item ->
        val detail = client.getFindingDetail(item.get("id").asText())
        val details = detail.get("details")
        EvalFinding.fromListItem(
            item,
            ownerLabel = details?.get("ownerLabel")?.takeUnless { it.isNull }?.asText(),
            attackerLabel = details?.get("attackerLabel")?.takeUnless { it.isNull }?.asText(),
        )
    }
}
