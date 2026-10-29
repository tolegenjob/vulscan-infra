package io.vulscan.lab.clean

import io.vulscan.lab.auth.UsersRepository
import io.vulscan.lab.auth.currentSession
import io.vulscan.lab.idor.DocumentsRepository
import io.vulscan.lab.idor.OrdersRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/**
 * Профиль `clean` (E-2, негативная цель): те же маршруты /api/orders/{id}, /api/documents/{id},
 * /api/admin/users, что у намеренно уязвимого [io.vulscan.lab.idor.IdorLabController]
 * (`@Profile("!clean")`, выключен здесь), но с проверкой владения/роли — логика идентична
 * [io.vulscan.lab.safe.SafeIdorController.order]/[io.vulscan.lab.safe.SafeIdorController.document]/
 * [io.vulscan.lab.safe.SafeIdorController.adminUsers].
 */
@RestController
@Profile("clean")
class CleanIdorController(
    private val orders: OrdersRepository,
    private val documents: DocumentsRepository,
    private val users: UsersRepository,
) {
    @GetMapping("/api/orders/{id}")
    fun order(@PathVariable id: Int, request: HttpServletRequest): ResponseEntity<Any> {
        val identity = request.currentSession()?.identity ?: return unauthorized()
        val order = orders.find(id) ?: return ResponseEntity.notFound().build()
        if (order.ownerUsername != identity.username) return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf("id" to order.id, "ownerUsername" to order.ownerUsername, "content" to order.content))
    }

    @GetMapping("/api/documents/{id}")
    fun document(@PathVariable id: Int, request: HttpServletRequest): ResponseEntity<Any> {
        val identity = request.currentSession()?.identity ?: return unauthorized()
        val document = documents.find(id) ?: return ResponseEntity.notFound().build()
        val allowed = document.ownerUsername == identity.username || documents.isSharedWith(id, identity.username)
        if (!allowed) return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf("id" to document.id, "ownerUsername" to document.ownerUsername, "content" to document.content))
    }

    @GetMapping("/api/admin/users")
    fun adminUsers(request: HttpServletRequest): ResponseEntity<Any> {
        val identity = request.currentSession()?.identity ?: return unauthorized()
        if (identity.role != "ADMIN") return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "forbidden"))
        val rows = users.findAll().map { mapOf("username" to it.username, "email" to it.email, "role" to it.role) }
        return ResponseEntity.ok(mapOf("users" to rows))
    }

    private fun unauthorized(): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "not authenticated"))
}
