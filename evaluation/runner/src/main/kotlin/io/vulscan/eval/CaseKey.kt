package io.vulscan.eval

/**
 * Нормализует finding/кейс ground truth к единому ключу сопоставления (план, T-1, раздел про
 * `ground-truth.yaml`): `method + " " + normalizedPathTemplate + " " + parameterName` (`-` если
 * параметра нет — пример плана: `GET /api/admin/users - admin->bob`), плюс
 * `" " + ownerLabel + "->" + attackerLabel` для IDOR.
 */
object CaseKey {
    fun of(finding: EvalFinding): String {
        val base = "${finding.method.uppercase()} ${pathOnly(finding.url)} ${finding.parameterName ?: "-"}"
        return if (finding.ownerLabel != null && finding.attackerLabel != null) {
            "$base ${finding.ownerLabel}->${finding.attackerLabel}"
        } else {
            base
        }
    }

    /** "http://lab-app:8080/api/orders/{id}" -> "/api/orders/{id}"; без завершающего "/" (кроме корня). */
    fun pathOnly(url: String): String {
        val withoutScheme = url.substringAfter("://", url)
        val path = "/" + withoutScheme.substringAfter('/', "")
        return if (path.length > 1 && path.endsWith('/')) path.dropLast(1) else path
    }
}
