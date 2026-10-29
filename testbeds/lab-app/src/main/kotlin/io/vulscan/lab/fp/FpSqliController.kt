package io.vulscan.lab.fp

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * «Шумовые» эндпоинты (T-1): ведут себя так, что наивный детектор мог бы принять их поведение за SQLi,
 * хотя под капотом инъекции нет вообще — все они `expectedVulnerable=false` в ground-truth.yaml.
 */
@RestController
class FpSqliController {
    private val rateLimitWindowStart = AtomicLong(System.currentTimeMillis())
    private val rateLimitCount = AtomicInteger(0)
    private val rateLimitWindowMs = 10_000L
    private val rateLimitMax = 5

    /** Всегда 500, независимо от содержимого параметра — не сигнал SQLi, а просто сломанный эндпоинт. */
    @GetMapping("/fp/sqli/always500")
    fun always500(@RequestParam(defaultValue = "") id: String): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("error" to "internal error"))

    /** Случайно меняющийся блок текста, не связанный со значением параметра — шум для сравнения ответов. */
    @GetMapping("/fp/sqli/random-block")
    fun randomBlock(@RequestParam(defaultValue = "") id: String): ResponseEntity<Any> {
        val block = if (Random.nextBoolean()) "block-a-${Random.nextInt(1000)}" else "block-b-${Random.nextInt(1000)}"
        return ResponseEntity.ok(mapOf("block" to block))
    }

    /** Всегда ждёт 3 секунды, независимо от параметра — проверяет, что детектор не примет это за time-based. */
    @GetMapping("/fp/sqli/slow")
    fun slow(@RequestParam(defaultValue = "") id: String): ResponseEntity<Any> {
        Thread.sleep(3000)
        return ResponseEntity.ok(mapOf("status" to "ok"))
    }

    /** Статическая страница, которая просто ЦИТИРУЕТ типичный текст ошибки СУБД как пример документации. */
    @GetMapping("/fp/sqli/sql-in-docs")
    fun sqlInDocs(@RequestParam(defaultValue = "") id: String): ResponseEntity<Any> =
        ResponseEntity.ok(
            mapOf(
                "page" to "docs",
                "content" to "Пример из документации: если вы видите фразу \"You have an error in your SQL syntax\", " +
                    "проверьте кавычки в запросе. Эта страница не выполняет запрос к БД вовсе.",
            ),
        )

    /** Имитация WAF: 403 на любую кавычку в параметре, даже когда сама операция не уязвима. */
    @GetMapping("/fp/sqli/waf")
    fun waf(@RequestParam(defaultValue = "") id: String): ResponseEntity<Any> =
        if (id.contains('\'') || id.contains('"')) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "blocked by WAF"))
        } else {
            ResponseEntity.ok(mapOf("status" to "ok"))
        }

    /** После N запросов в окне — 429, независимо от параметра (проверяет, что это не путают с WAF/SQLi-сигналом). */
    @GetMapping("/fp/sqli/rate-limit")
    fun rateLimit(@RequestParam(defaultValue = "") id: String): ResponseEntity<Any> {
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (now - rateLimitWindowStart.get() > rateLimitWindowMs) {
                rateLimitWindowStart.set(now)
                rateLimitCount.set(0)
            }
        }
        val count = rateLimitCount.incrementAndGet()
        return if (count > rateLimitMax) {
            ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(mapOf("error" to "rate limited"))
        } else {
            ResponseEntity.ok(mapOf("status" to "ok"))
        }
    }
}
