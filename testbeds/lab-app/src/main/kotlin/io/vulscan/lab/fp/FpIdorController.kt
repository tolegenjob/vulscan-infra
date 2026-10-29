package io.vulscan.lab.fp

import io.vulscan.lab.auth.SESSION_COOKIE
import io.vulscan.lab.auth.SessionStore
import io.vulscan.lab.auth.currentSession
import io.vulscan.lab.idor.OrdersRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/** «Шумовые» IDOR-эндпоинты (T-1): не текут данные владельца, но и не выглядят как обычный отказ. */
@RestController
class FpIdorController(
    private val orders: OrdersRepository,
    private val sessions: SessionStore,
    @Value("\${lab.slow-session-max-hits:2}") private val slowSessionMaxHits: Int,
) {
    /** Чужой заказ отдаёт 200 с пустым объектом — не 403/404, но и ownerEmail в нём никогда нет. */
    @GetMapping("/api/fp/orders/{id}")
    fun order(@PathVariable id: Int, request: HttpServletRequest): ResponseEntity<Any> {
        val identity = request.currentSession()?.identity
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "not authenticated"))
        val order = orders.find(id) ?: return ResponseEntity.ok(emptyMap<String, Any>())
        if (order.ownerUsername != identity.username) return ResponseEntity.ok(emptyMap<String, Any>())
        return ResponseEntity.ok(mapOf("id" to order.id, "ownerUsername" to order.ownerUsername, "content" to order.content))
    }

    /**
     * Протухающая сессия: после [slowSessionMaxHits] вызовов сессия реально удаляется из [SessionStore] —
     * cookie становится недействительным для ЛЮБОГО эндпоинта, не только этого (иначе re-login IDOR-модуля
     * просто обошёл бы симуляцию, получив новую сессию).
     */
    @GetMapping("/api/fp/slow-session")
    fun slowSession(request: HttpServletRequest): ResponseEntity<Any> {
        val session = request.currentSession()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "not authenticated"))
        session.slowSessionHits += 1
        if (session.slowSessionHits > slowSessionMaxHits) {
            sessions.invalidate(request.cookies?.firstOrNull { it.name == SESSION_COOKIE }?.value)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "session expired"))
        }
        return ResponseEntity.ok(mapOf("status" to "ok", "hits" to session.slowSessionHits))
    }
}
