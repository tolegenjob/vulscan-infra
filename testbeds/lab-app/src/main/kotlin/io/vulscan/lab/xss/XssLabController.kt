package io.vulscan.lab.xss

import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Намеренно уязвимые reflected/stored XSS-эндпоинты (T-1 XSS-01…XSS-05). Параметр `q` отражается
 * без какого-либо кодирования — ровно те четыре контекста, которые ожидает XSS-модуль.
 *
 * `@Profile("!clean")`: под профилем `clean` (E-2, негативная цель) те же маршруты отдаёт
 * [io.vulscan.lab.clean.CleanXssController] — без единой XSS-уязвимости.
 */
@RestController
@Profile("!clean")
class XssLabController {
    private val guestbook = CopyOnWriteArrayList<String>()

    private fun html(body: String) =
        ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body("<html><body>$body</body></html>")

    /** XSS-01: HTML_TEXT. */
    @GetMapping("/xss/reflect-text")
    fun reflectText(@RequestParam(defaultValue = "") q: String) = html("<p>Результат поиска: $q</p>")

    /** XSS-02: ATTR_DOUBLE_QUOTED. */
    @GetMapping("/xss/reflect-attr-dq")
    fun reflectAttrDq(@RequestParam(defaultValue = "") q: String) =
        html("<input type=\"text\" name=\"q\" value=\"$q\">")

    /** XSS-03: ATTR_SINGLE_QUOTED. */
    @GetMapping("/xss/reflect-attr-sq")
    fun reflectAttrSq(@RequestParam(defaultValue = "") q: String) =
        html("<input type='text' name='q' value='$q'>")

    /** XSS-04: SCRIPT_STRING_DQ. */
    @GetMapping("/xss/reflect-js-string")
    fun reflectJsString(@RequestParam(defaultValue = "") q: String) =
        html("<script>var lastQuery = \"$q\";</script>")

    /** XSS-05: stored (guestbook). POST добавляет запись без санитизации, GET отображает все записи как есть. */
    @PostMapping("/xss/stored-guestbook")
    fun postGuestbook(@RequestParam(defaultValue = "") message: String): ResponseEntity<Void> {
        guestbook += message
        return ResponseEntity.noContent().header(HttpHeaders.LOCATION, "/xss/stored-guestbook").build()
    }

    @GetMapping("/xss/stored-guestbook")
    fun getGuestbook(): ResponseEntity<String> {
        val entries = guestbook.joinToString("") { "<li>$it</li>" }
        return html("<h1>Гостевая книга</h1><ul>$entries</ul>")
    }
}
