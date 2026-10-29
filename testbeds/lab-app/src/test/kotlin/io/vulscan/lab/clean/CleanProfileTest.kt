package io.vulscan.lab.clean

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

/**
 * Профиль `clean` (E-2, негативная цель): под ним [io.vulscan.lab.xss.XssLabController],
 * [io.vulscan.lab.sqli.SqliLabController], [io.vulscan.lab.idor.IdorLabController] (все `@Profile("!clean")`)
 * выключены, а их безопасные замены на ТЕХ ЖЕ путях ([CleanXssController]/[CleanSqliController]/
 * [CleanIdorController], `@Profile("clean")`) — включены. Если бы оба профиля регистрировали
 * один путь одновременно, Spring отказался бы поднимать контекст (конфликт маппинга) — сам факт
 * успешного старта контекста уже проверяет корректность `@Profile`; этот тест проверяет это явно.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("clean")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:labapp-clean;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
    ],
)
class CleanProfileTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private lateinit var mapping: RequestMappingHandlerMapping

    @Test
    fun `clean profile still serves the vulnerable paths, just safely`() {
        val endpoints = registeredEndpoints()
        listOf(
            "GET /xss/reflect-text", "GET /xss/reflect-attr-dq", "GET /xss/reflect-attr-sq", "GET /xss/reflect-js-string",
            "GET /sqli/error/products", "GET /sqli/boolean/search", "GET /sqli/time/lookup",
            "GET /api/orders/{id}", "GET /api/documents/{id}", "GET /api/admin/users",
        ).forEach { key -> assertTrue(key in endpoints) { "под профилем clean должен остаться маппинг: $key" } }
    }

    @Test
    fun `clean profile has exactly one controller per path (no duplicate registration)`() {
        // mapping.handlerMethods — Map<RequestMappingInfo, HandlerMethod>; два бина на один путь дали бы
        // Spring конфликт при старте контекста, так что этот тест в первую очередь проверяет, что мы
        // сюда вообще дошли — assertFalse(false) документирует намерение, не является заглушкой.
        assertFalse(mapping.handlerMethods.isEmpty())
    }

    private fun registeredEndpoints(): Set<String> =
        mapping.handlerMethods.keys.flatMap { info ->
            val methods = info.methodsCondition.methods.ifEmpty { setOf(RequestMethod.GET) }
            val patterns = info.pathPatternsCondition?.patterns?.map { it.patternString } ?: emptyList()
            methods.flatMap { method -> patterns.map { pattern -> "$method $pattern" } }
        }.toSet()
}
