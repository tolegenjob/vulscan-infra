package io.vulscan.lab.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

const val SESSION_COOKIE = "LABSESSION"
const val SESSION_ATTRIBUTE = "io.vulscan.lab.session"

/**
 * Читает cookie LABSESSION, кладёт найденную [LabSession] в атрибут запроса — контроллеры сами решают,
 * что делать с её отсутствием (401) или ролью (403). Сам фильтр ничего не блокирует.
 */
@Component
class LabAuthFilter(private val sessions: SessionStore) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val token = request.cookies?.firstOrNull { it.name == SESSION_COOKIE }?.value
        val session = sessions.get(token)
        if (session != null) request.setAttribute(SESSION_ATTRIBUTE, session)
        chain.doFilter(request, response)
    }
}

fun HttpServletRequest.currentSession(): LabSession? = getAttribute(SESSION_ATTRIBUTE) as LabSession?
