package io.vulscan.lab.safe

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Безопасные двойники маршрутов /sqli/... — тот же интерфейс, параметризованные запросы. */
@RestController
class SafeSqliController(private val jdbc: JdbcTemplate) {

    @GetMapping("/safe/sqli/products")
    fun products(@RequestParam(defaultValue = "") id: String): ResponseEntity<Any> {
        val numericId = id.toIntOrNull()
            ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "id must be numeric"))
        val rows = jdbc.queryForList("SELECT id, name, price, description FROM products WHERE id = ?", numericId)
        return ResponseEntity.ok(rows)
    }

    @GetMapping("/safe/sqli/search")
    fun search(@RequestParam(defaultValue = "") name: String): ResponseEntity<Any> {
        val rows = jdbc.queryForList("SELECT id, name FROM products WHERE name = ?", name)
        return ResponseEntity.ok(mapOf("found" to rows.isNotEmpty(), "results" to rows))
    }
}
