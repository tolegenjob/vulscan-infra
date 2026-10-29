package io.vulscan.lab

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream

/**
 * T-1: ground-truth.yaml не редактируется под результаты сканера, но сам файл обязан оставаться
 * внутренне согласованным. Этот тест проверяет три инварианта из плана:
 *  1) каждый `key` соответствует реально зарегистрированному маппингу контроллера (через рефлексию
 *     Spring MVC — [RequestMappingHandlerMapping], а не ручной парсинг аннотаций);
 *  2) у каждого случая с `expectedVulnerable=true` есть безопасный двойник (`safeDoubleOf`),
 *     и этот двойник существует и сам помечен `expectedVulnerable=false`;
 *  3) `id` не дублируются.
 *
 * Контекст поднимается без реального Postgres: datasource переопределён на in-memory H2 в режиме
 * совместимости с PostgreSQL, чтобы schema.sql/seed.sql (стандартный SQL, без специфики диалекта)
 * применились без изменений.
 */
// MOCK (а не NONE) обязателен: RequestMappingHandlerMapping регистрируется только в веб-контексте,
// но реальный сервер при этом не поднимается.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:labapp-groundtruth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
    ],
)
class GroundTruthConsistencyTest {

    // Actuator добавляет свой собственный controllerEndpointHandlerMapping того же типа —
    // нужен именно bean веб-MVC контроллеров, а не actuator-эндпоинтов.
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private lateinit var mapping: RequestMappingHandlerMapping

    private data class Case(
        val id: String,
        val module: String,
        val key: String,
        val expectedVulnerable: Boolean,
        val safeDoubleOf: String?,
    )

    @Test
    fun `every key resolves to a real controller mapping`() {
        val endpoints = registeredEndpoints()
        val missing = loadCases().filter { endpointPartOf(it.key) !in endpoints }
        assertTrue(missing.isEmpty()) {
            "ground-truth.yaml ссылается на несуществующие маппинги контроллеров: " +
                missing.joinToString { "${it.id} -> ${it.key}" }
        }
    }

    @Test
    fun `case ids are unique`() {
        val duplicates = loadCases().groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue(duplicates.isEmpty()) { "дублирующиеся id в ground-truth.yaml: $duplicates" }
    }

    @Test
    fun `every vulnerable case has a safe double that is itself not vulnerable`() {
        val cases = loadCases()
        val byId = cases.associateBy { it.id }
        val vulnerable = cases.filter { it.expectedVulnerable }

        val withoutLink = vulnerable.filter { it.safeDoubleOf == null }
        assertTrue(withoutLink.isEmpty()) {
            "у следующих expectedVulnerable=true случаев не указан safeDoubleOf: ${withoutLink.map { it.id }}"
        }

        val danglingOrVulnerable = vulnerable.mapNotNull { case ->
            val double = byId[case.safeDoubleOf]
            when {
                double == null -> "${case.id} -> safeDoubleOf '${case.safeDoubleOf}' не найден среди id"
                double.expectedVulnerable -> "${case.id} -> safeDoubleOf '${case.safeDoubleOf}' сам помечен expectedVulnerable=true"
                else -> null
            }
        }
        assertTrue(danglingOrVulnerable.isEmpty()) { danglingOrVulnerable.joinToString("; ") }
    }

    // --- вспомогательные методы ---

    private fun loadCases(): List<Case> {
        val yaml = Yaml()
        val root = FileInputStream(findGroundTruthFile()).use {
            @Suppress("UNCHECKED_CAST")
            yaml.load(it) as Map<String, Any>
        }
        @Suppress("UNCHECKED_CAST")
        val rawCases = root["cases"] as List<Map<String, Any?>>
        return rawCases.map {
            Case(
                id = it["id"] as String,
                module = it["module"] as String,
                key = it["key"] as String,
                expectedVulnerable = it["expectedVulnerable"] as Boolean,
                safeDoubleOf = it["safeDoubleOf"] as String?,
            )
        }
    }

    /** Gradle запускает тесты из каталога модуля — ground-truth.yaml лежит прямо в его корне. */
    private fun findGroundTruthFile(): File {
        var dir: File? = File(".").absoluteFile
        repeat(6) {
            val candidate = File(dir, "ground-truth.yaml")
            if (candidate.exists()) return candidate
            dir = dir?.parentFile
        }
        error("ground-truth.yaml не найден (искали от ${File(".").absolutePath} вверх)")
    }

    /** "METHOD path" из ключа вида "GET /api/orders/{id} id alice->bob" или "GET /api/admin/users - admin->bob". */
    private fun endpointPartOf(key: String): String {
        val parts = key.split(" ")
        require(parts.size >= 2) { "некорректный key: '$key'" }
        return "${parts[0]} ${parts[1]}"
    }

    /** Множество реально зарегистрированных Spring MVC маппингов в виде "METHOD pathTemplate". */
    private fun registeredEndpoints(): Set<String> =
        mapping.handlerMethods.keys.flatMap { info ->
            val methods = info.methodsCondition.methods.ifEmpty { setOf(RequestMethod.GET) }
            val patterns = info.pathPatternsCondition?.patterns?.map { it.patternString } ?: emptyList()
            methods.flatMap { method -> patterns.map { pattern -> "$method $pattern" } }
        }.toSet()
}
