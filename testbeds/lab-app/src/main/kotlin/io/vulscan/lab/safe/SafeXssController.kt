package io.vulscan.lab.safe

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private fun escapeHtml(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

/** Безопасные двойники XSS-эндпоинтов (T-1 XSS-S01…XSS-S05) — тот же интерфейс, без уязвимости. */
@RestController
class SafeXssController {
    private fun html(body: String) =
        ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body("<html><body>$body</body></html>")

    /** XSS-S01: значение кодируется перед вставкой в HTML_TEXT. */
    @GetMapping("/safe/xss/encoded-text")
    fun encodedText(@RequestParam(defaultValue = "") q: String) = html("<p>Результат поиска: ${escapeHtml(q)}</p>")

    /** XSS-S02: значение кодируется перед вставкой в атрибут. */
    @GetMapping("/safe/xss/encoded-attr")
    fun encodedAttr(@RequestParam(defaultValue = "") q: String) =
        html("<input type=\"text\" name=\"q\" value=\"${escapeHtml(q)}\">")

    /** XSS-S03: ответ — JSON, браузер не исполнит его как HTML/JS, даже если значение отражено «как есть». */
    @GetMapping("/safe/xss/json-echo")
    fun jsonEcho(@RequestParam(defaultValue = "") q: String): ResponseEntity<Map<String, String>> =
        ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(mapOf("q" to q))

    /** XSS-S04: параметр вообще не отражается в ответе. */
    @GetMapping("/safe/xss/no-reflect")
    fun noReflect(@RequestParam(defaultValue = "") q: String) = html("<p>Поиск выполнен.</p>")

    /** XSS-S05: значение в textarea, закодировано. */
    @GetMapping("/safe/xss/textarea-encoded")
    fun textareaEncoded(@RequestParam(defaultValue = "") q: String) = html("<textarea>${escapeHtml(q)}</textarea>")
}
