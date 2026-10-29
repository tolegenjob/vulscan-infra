package io.vulscan.lab.clean

import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CopyOnWriteArrayList

private fun escapeHtml(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

/**
 * Профиль `clean` (E-2, негативная цель): те же маршруты, что у намеренно уязвимого
 * [io.vulscan.lab.xss.XssLabController] (`@Profile("!clean")`, выключен здесь), но без единой
 * XSS-уязвимости — для измерения, что сканер не даёт findings там, где их нет (TP=0, FP=0 ожидаются).
 * Логика кодирования идентична [io.vulscan.lab.safe.SafeXssController].
 */
@RestController
@Profile("clean")
class CleanXssController {
    private val guestbook = CopyOnWriteArrayList<String>()

    private fun html(body: String) =
        ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body("<html><body>$body</body></html>")

    @GetMapping("/xss/reflect-text")
    fun reflectText(@RequestParam(defaultValue = "") q: String) = html("<p>Результат поиска: ${escapeHtml(q)}</p>")

    @GetMapping("/xss/reflect-attr-dq")
    fun reflectAttrDq(@RequestParam(defaultValue = "") q: String) =
        html("<input type=\"text\" name=\"q\" value=\"${escapeHtml(q)}\">")

    @GetMapping("/xss/reflect-attr-sq")
    fun reflectAttrSq(@RequestParam(defaultValue = "") q: String) =
        html("<input type='text' name='q' value='${escapeHtml(q)}'>")

    @GetMapping("/xss/reflect-js-string")
    fun reflectJsString(@RequestParam(defaultValue = "") q: String) =
        html("<script>var lastQuery = \"${escapeHtml(q)}\";</script>")

    @PostMapping("/xss/stored-guestbook")
    fun postGuestbook(@RequestParam(defaultValue = "") message: String): ResponseEntity<Void> {
        guestbook += message
        return ResponseEntity.noContent().header(HttpHeaders.LOCATION, "/xss/stored-guestbook").build()
    }

    @GetMapping("/xss/stored-guestbook")
    fun getGuestbook(): ResponseEntity<String> {
        val entries = guestbook.joinToString("") { "<li>${escapeHtml(it)}</li>" }
        return html("<h1>Гостевая книга</h1><ul>$entries</ul>")
    }
}
