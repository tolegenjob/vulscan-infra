package io.vulscan.lab.auth

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** Хранилище сессий в памяти — отдельный процесс на стенд, перезапуск сбрасывает все сессии. */
@Component
class SessionStore {
    private val sessions = ConcurrentHashMap<String, LabSession>()
    private val random = SecureRandom()

    fun create(identity: LabIdentity): String {
        val token = newToken()
        sessions[token] = LabSession(identity)
        return token
    }

    fun get(token: String?): LabSession? = token?.let { sessions[it] }

    fun invalidate(token: String?) {
        if (token != null) sessions.remove(token)
    }

    private fun newToken(): String {
        val bytes = ByteArray(24)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
