package io.vulscan.lab.auth

/** Текущий «залогиненный» пользователь стенда (I-1 IDOR-матрицы: alice/bob — USER, admin — ADMIN). */
data class LabIdentity(val username: String, val email: String, val role: String)

/** Запись о живой сессии: кто залогинен и сколько раз был вызван /api/fp/slow-session (I-6 «шум»). */
class LabSession(val identity: LabIdentity) {
    @Volatile var slowSessionHits: Int = 0
}
