package io.vulscan.lab.auth

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class UsersRepository(private val jdbc: JdbcTemplate) {
    /** Сравнение пароля — прямое, без хеширования: это стенд для измерения качества сканера, не образец. */
    fun authenticate(username: String, password: String): LabIdentity? =
        jdbc.query(
            "SELECT username, email, role FROM users WHERE username = ? AND password = ?",
            { rs, _ -> LabIdentity(rs.getString("username"), rs.getString("email"), rs.getString("role")) },
            username, password,
        ).firstOrNull()

    fun findAll(): List<LabIdentity> =
        jdbc.query("SELECT username, email, role FROM users ORDER BY username") { rs, _ ->
            LabIdentity(rs.getString("username"), rs.getString("email"), rs.getString("role"))
        }
}
