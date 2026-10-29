package io.vulscan.lab

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class IndexController {
    @GetMapping("/")
    fun index(): ResponseEntity<String> = ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(
        """
        <html><body>
        <h1>lab-app</h1>
        <p><strong>Внимание: это намеренно уязвимое учебное приложение.</strong>
        Не разворачивайте его за пределами изолированной тестовой сети и не используйте настоящие данные.</p>
        </body></html>
        """.trimIndent(),
    )
}
