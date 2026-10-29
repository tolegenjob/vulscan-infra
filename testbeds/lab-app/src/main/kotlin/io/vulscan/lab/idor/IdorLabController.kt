package io.vulscan.lab.idor

import io.vulscan.lab.auth.UsersRepository
import io.vulscan.lab.auth.currentSession
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/**
 * Намеренно уязвимые IDOR-эндпоинты (T-1 IDOR-01…IDOR-08): проверяется только факт входа, не владение
 * объектом/роль. Безопасные двойники — в [io.vulscan.lab.safe.SafeIdorController].
 */
@RestController
class IdorLabController(
    private val orders: OrdersRepository,
    private val documents: DocumentsRepository,
    private val posts: PostsRepository,
    private val users: UsersRepository,
) {
    /** IDOR-01…04 (horizontal): любой залогиненный пользователь читает ЛЮБОЙ заказ, не только свой. */
    @GetMapping("/api/orders/{id}")
    fun order(@PathVariable id: Int, request: HttpServletRequest): ResponseEntity<Any> {
        if (request.currentSession() == null) return unauthorized()
        val order = orders.find(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf("id" to order.id, "ownerUsername" to order.ownerUsername, "ownerEmail" to order.ownerEmail, "content" to order.content))
    }

    /** IDOR-05…07 (horizontal): проверяется только аутентификация, не владение/shared_with. */
    @GetMapping("/api/documents/{id}")
    fun document(@PathVariable id: Int, request: HttpServletRequest): ResponseEntity<Any> {
        if (request.currentSession() == null) return unauthorized()
        val document = documents.find(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf("id" to document.id, "ownerUsername" to document.ownerUsername, "content" to document.content))
    }

    /** IDOR-08 (vertical): нет проверки роли — любой залогиненный пользователь видит весь список. */
    @GetMapping("/api/admin/users")
    fun adminUsers(request: HttpServletRequest): ResponseEntity<Any> {
        if (request.currentSession() == null) return unauthorized()
        val rows = users.findAll().map { mapOf("username" to it.username, "email" to it.email, "role" to it.role) }
        return ResponseEntity.ok(mapOf("users" to rows))
    }

    private fun unauthorized() = ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "not authenticated"))
}
