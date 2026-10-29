package io.vulscan.lab

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * lab-app (T-1): намеренно уязвимое учебное приложение для измерения качества сканера.
 * Слушает только внутренний docker-сеть lab-net; порт на хост по умолчанию не публикуется (см. infra/).
 */
@SpringBootApplication
class LabApplication

fun main(args: Array<String>) {
    runApplication<LabApplication>(*args)
}
