package io.vulscan.lab.auth

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthController(private val users: UsersRepository, private val sessions: SessionStore) {

    /** Форма-логин (как ожидает FormLoginAuthenticator IDOR-модуля): username/password, cookie-сессия в ответ. */
    @PostMapping("/login")
    fun login(
        @RequestParam username: String,
        @RequestParam password: String,
        response: HttpServletResponse,
    ): ResponseEntity<Map<String, Any>> {
        val identity = users.authenticate(username, password)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "invalid credentials"))

        val token = sessions.create(identity)
        val cookie = Cookie(SESSION_COOKIE, token).apply {
            path = "/"
            isHttpOnly = true
            maxAge = 3600
        }
        response.addCookie(cookie)
        return ResponseEntity.ok(mapOf("username" to identity.username, "role" to identity.role))
    }

    @GetMapping("/api/me")
    fun me(request: HttpServletRequest): ResponseEntity<Map<String, Any>> {
        val identity = request.currentSession()?.identity
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "not authenticated"))
        return ResponseEntity.ok(mapOf("username" to identity.username, "email" to identity.email, "role" to identity.role))
    }
}
