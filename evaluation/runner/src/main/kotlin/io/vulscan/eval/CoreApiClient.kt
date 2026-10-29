package io.vulscan.eval

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.NullNode
import io.vulscan.kit.contracts.Json
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class CoreApiException(message: String, val status: Int, val responseBody: String) : RuntimeException(message)

/**
 * Тонкий HTTP-клиент `Core`-API (`/api/v1`) для evaluation/runner: сессия (cookie) + CSRF
 * (double-submit `XSRF-TOKEN`/`X-XSRF-TOKEN`, см. `SecurityConfig` в core-backend). Разбирает ответы
 * как [JsonNode] — свои типизированные DTO Core здесь не нужны, достаточного покрытия даёт
 * `io.vulscan:contracts` (Json/перечисления).
 */
class CoreApiClient(baseUrl: String) {
    private val apiBase = baseUrl.trimEnd('/') + "/api/v1"
    private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ALL)
    private val http: HttpClient = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(10)).build()
    private var csrfToken: String? = null

    fun login(username: String, password: String): JsonNode {
        val response = send("POST", "/auth/login", Json.write(mapOf("username" to username, "password" to password)), authenticated = false)
        csrfToken = cookies.cookieStore.cookies.firstOrNull { it.name == "XSRF-TOKEN" }?.value
        require(csrfToken != null) { "Логин выполнен, но cookie XSRF-TOKEN не получена" }
        return response
    }

    fun createTarget(name: String, targetBaseUrl: String): JsonNode =
        send("POST", "/targets", Json.write(mapOf("name" to name, "baseUrl" to targetBaseUrl)))

    fun verifyTarget(id: String): JsonNode = send("POST", "/targets/$id/verify", "{}")

    fun approveTarget(id: String): JsonNode = send("POST", "/targets/$id/approve", "{}")

    fun createEndpoint(targetId: String, method: String, url: String, parameters: List<Map<String, Any?>>, idempotent: Boolean): JsonNode =
        send(
            "POST",
            "/targets/$targetId/endpoints",
            Json.write(mapOf("method" to method, "url" to url, "parameters" to parameters, "idempotent" to idempotent)),
        )

    fun createScan(targetId: String, modules: List<String>, endpointIds: List<String>, extra: Map<String, Any?> = emptyMap()): JsonNode {
        val payload = LinkedHashMap<String, Any?>()
        payload["targetId"] = targetId
        payload["modules"] = modules
        payload["endpointIds"] = endpointIds
        payload.putAll(extra)
        return send("POST", "/scans", Json.write(payload))
    }

    fun getScan(id: String): JsonNode = send("GET", "/scans/$id")

    /** Одна страница `GET /findings?scanId=...`. */
    fun listFindingsPage(scanId: String, page: Int, size: Int): JsonNode = send("GET", "/findings?scanId=$scanId&page=$page&size=$size")

    /** Все findings скана, постранично (план, E-1.1: `size=100`). */
    fun listAllFindings(scanId: String, pageSize: Int = 100): List<JsonNode> {
        val items = mutableListOf<JsonNode>()
        var page = 0
        while (true) {
            val response = listFindingsPage(scanId, page, pageSize)
            response.get("items").forEach { items += it }
            val total = response.get("total").asLong()
            if (items.size.toLong() >= total || response.get("items").size() == 0) break
            page++
        }
        return items
    }

    fun getFindingDetail(id: String): JsonNode = send("GET", "/findings/$id")

    private fun send(method: String, path: String, body: String? = null, authenticated: Boolean = true): JsonNode {
        val builder = HttpRequest.newBuilder(URI.create("$apiBase$path")).header("Content-Type", "application/json")
        if (authenticated) csrfToken?.let { builder.header("X-XSRF-TOKEN", it) }
        val publisher = if (body != null) HttpRequest.BodyPublishers.ofString(body) else HttpRequest.BodyPublishers.noBody()
        builder.method(method, publisher)
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw CoreApiException("$method $apiBase$path -> HTTP ${response.statusCode()}", response.statusCode(), response.body())
        }
        val text = response.body()
        return if (text.isNullOrBlank()) NullNode.getInstance() else Json.mapper.readTree(text)
    }
}
