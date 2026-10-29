package io.vulscan.lab

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/**
 * `lab-app` принадлежит той же команде, что и сканер, поэтому подтверждение владения (N-1, Core
 * `TargetVerificationService`) автоматизировано: любой токен, который Core вставляет в путь
 * `/.well-known/vulnscan-<token>.txt`, отражается обратно как содержимое файла. Для стороннего,
 * не принадлежащего команде приложения такого эндпоинта, разумеется, не будет — владелец размещает
 * файл с токеном вручную.
 */
@RestController
class WellKnownController {
    @GetMapping("/.well-known/vulnscan-{token}.txt", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun verify(@PathVariable token: String): ResponseEntity<String> =
        ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(token)
}
