package io.vulscan.lab.clean

import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Профиль `clean` (E-2, негативная цель): те же маршруты /sqli/..., что у намеренно уязвимого
 * [io.vulscan.lab.sqli.SqliLabController] (`@Profile("!clean")`, выключен здесь), но параметризованные
 * запросы — без единой SQL-инъекции. Логика идентична [io.vulscan.lab.safe.SafeSqliController].
 */
@RestController
@Profile("clean")
class CleanSqliController(private val jdbc: JdbcTemplate) {

    @GetMapping("/sqli/error/products")
    fun errorBased(@RequestParam(defaultValue = "") id: String): ResponseEntity<Any> {
        val numericId = id.toIntOrNull()
            ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "id must be numeric"))
        return ResponseEntity.ok(jdbc.queryForList("SELECT id, name, price, description FROM products WHERE id = ?", numericId))
    }

    @GetMapping("/sqli/boolean/search")
    fun booleanBased(@RequestParam(defaultValue = "") name: String): ResponseEntity<Any> {
        val rows = jdbc.queryForList("SELECT id, name FROM products WHERE name = ?", name)
        return ResponseEntity.ok(mapOf("found" to rows.isNotEmpty(), "results" to rows))
    }

    @GetMapping("/sqli/time/lookup")
    fun timeBased(@RequestParam(defaultValue = "") code: String): ResponseEntity<Any> {
        jdbc.queryForList("SELECT id FROM products WHERE code = ?", code)
        return ResponseEntity.ok(mapOf("status" to "lookup complete"))
    }
}
