package io.vulscan.lab.idor

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

data class OrderRow(val id: Int, val ownerUsername: String, val ownerEmail: String, val content: String)
data class DocumentRow(val id: Int, val ownerUsername: String, val content: String)
data class PostRow(val id: Int, val authorUsername: String, val content: String)

@Repository
class OrdersRepository(private val jdbc: JdbcTemplate) {
    fun find(id: Int): OrderRow? = jdbc.query(
        """
        SELECT o.id, o.owner_username, u.email AS owner_email, o.content
        FROM orders o JOIN users u ON u.username = o.owner_username
        WHERE o.id = ?
        """.trimIndent(),
        { rs, _ -> OrderRow(rs.getInt("id"), rs.getString("owner_username"), rs.getString("owner_email"), rs.getString("content")) },
        id,
    ).firstOrNull()
}

@Repository
class DocumentsRepository(private val jdbc: JdbcTemplate) {
    fun find(id: Int): DocumentRow? = jdbc.query(
        "SELECT id, owner_username, content FROM documents WHERE id = ?",
        { rs, _ -> DocumentRow(rs.getInt("id"), rs.getString("owner_username"), rs.getString("content")) },
        id,
    ).firstOrNull()

    fun isSharedWith(documentId: Int, username: String): Boolean = jdbc.queryForObject(
        "SELECT COUNT(*) FROM document_shares WHERE document_id = ? AND shared_with_username = ?",
        Int::class.java, documentId, username,
    ) ?: 0 > 0
}

@Repository
class PostsRepository(private val jdbc: JdbcTemplate) {
    fun find(id: Int): PostRow? = jdbc.query(
        "SELECT id, author_username, content FROM posts WHERE id = ?",
        { rs, _ -> PostRow(rs.getInt("id"), rs.getString("author_username"), rs.getString("content")) },
        id,
    ).firstOrNull()
}
