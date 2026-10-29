package io.vulscan.lab.safe

import io.vulscan.lab.auth.currentSession
import io.vulscan.lab.idor.DocumentsRepository
import io.vulscan.lab.idor.OrdersRepository
import io.vulscan.lab.idor.PostsRepository
import io.vulscan.lab.auth.UsersRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/** Безопасные двойники /api/** — тот же интерфейс, с проверкой владения/роли/публичности. */
@RestController
class SafeIdorController(
    private val orders: OrdersRepository,
    private val documents: DocumentsRepository,
    private val posts: PostsRepository,
    private val users: UsersRepository,
) {
    /** Владелец или 404 — чужой заказ выглядит так же, как несуществующий. */
    @GetMapping("/api/safe/orders/{id}")
    fun order(@PathVariable id: Int, request: HttpServletRequest): ResponseEntity<Any> {
        val identity = request.currentSession()?.identity ?: return unauthorized()
        val order = orders.find(id) ?: return ResponseEntity.notFound().build()
        if (order.ownerUsername != identity.username) return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf("id" to order.id, "ownerUsername" to order.ownerUsername, "content" to order.content))
    }

    /** Владелец или явно расшарено (document_shares) — иначе 404. */
    @GetMapping("/api/safe/documents/{id}")
    fun document(@PathVariable id: Int, request: HttpServletRequest): ResponseEntity<Any> {
        val identity = request.currentSession()?.identity ?: return unauthorized()
        val document = documents.find(id) ?: return ResponseEntity.notFound().build()
        val allowed = document.ownerUsername == identity.username || documents.isSharedWith(id, identity.username)
        if (!allowed) return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf("id" to document.id, "ownerUsername" to document.ownerUsername, "content" to document.content))
    }

    /** Публичный пост — доступен всем, авторизация не требуется (это НЕ IDOR, это предназначение эндпоинта). */
    @GetMapping("/api/posts/{id}")
    fun post(@PathVariable id: Int): ResponseEntity<Any> {
        val post = posts.find(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf("id" to post.id, "authorUsername" to post.authorUsername, "content" to post.content))
    }

    /** Требует роль ADMIN — иначе 403 (не 404: наличие функции не скрывается, только доступ к ней). */
    @GetMapping("/api/safe/admin/users")
    fun adminUsers(request: HttpServletRequest): ResponseEntity<Any> {
        val identity = request.currentSession()?.identity ?: return unauthorized()
        if (identity.role != "ADMIN") return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "forbidden"))
        val rows = users.findAll().map { mapOf("username" to it.username, "email" to it.email, "role" to it.role) }
        return ResponseEntity.ok(mapOf("users" to rows))
    }

    private fun unauthorized() = ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "not authenticated"))
}
