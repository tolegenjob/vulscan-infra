package io.vulscan.lab.sqli

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Намеренно уязвимые SQLi-эндпоинты (T-1): прямая конкатенация строки запроса в JDBC, без PreparedStatement.
 * Уязвимость — ТОЛЬКО здесь, в `/sqli/**`; безопасные двойники — в [io.vulscan.lab.safe.SafeSqliController].
 */
@RestController
class SqliLabController(private val jdbc: JdbcTemplate) {

    /** error-based: ошибка СУБД (с текстом) возвращается прямо в ответе. */
    @GetMapping("/sqli/error/products")
    fun errorBased(@RequestParam(defaultValue = "") id: String): ResponseEntity<Any> {
        val sql = "SELECT id, name, price, description FROM products WHERE id = $id"
        return try {
            ResponseEntity.ok(jdbc.queryForList(sql))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to (e.cause?.message ?: e.message ?: "database error")))
        }
    }

    /** boolean-based: ошибки скрыты, но наличие/отсутствие строк в ответе зависит от истинности условия. */
    @GetMapping("/sqli/boolean/search")
    fun booleanBased(@RequestParam(defaultValue = "") name: String): ResponseEntity<Any> {
        val sql = "SELECT id, name FROM products WHERE name = '$name'"
        return try {
            val rows = jdbc.queryForList(sql)
            if (rows.isEmpty()) {
                ResponseEntity.ok(mapOf("found" to false, "results" to emptyList<Any>()))
            } else {
                ResponseEntity.ok(mapOf("found" to true, "results" to rows))
            }
        } catch (e: Exception) {
            // Ошибка скрывается — наружу отдаётся тот же вид ответа, что и для «не найдено».
            ResponseEntity.ok(mapOf("found" to false, "results" to emptyList<Any>()))
        }
    }

    /** time-based: ответ всегда одинаковый по форме; различие — только в задержке выполнения. */
    @GetMapping("/sqli/time/lookup")
    fun timeBased(@RequestParam(defaultValue = "") code: String): ResponseEntity<Any> {
        val sql = "SELECT id FROM products WHERE code = '$code'"
        runCatching { jdbc.queryForList(sql) }
        return ResponseEntity.ok(mapOf("status" to "lookup complete"))
    }
}
