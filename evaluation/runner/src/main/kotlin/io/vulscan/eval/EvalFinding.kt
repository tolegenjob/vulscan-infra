package io.vulscan.eval

import tools.jackson.databind.JsonNode
import io.vulscan.kit.contracts.Verdict

/**
 * То немногое из finding Core, что нужно [CaseKey]/[Matcher]: метод, URL эндпоинта (шаблон, как он
 * зарегистрирован в Core — то же значение, что и `FindingPayload.url`), имя параметра, вердикт, и
 * (только для IDOR) метки владельца/атакующего из `details.ownerLabel`/`details.attackerLabel`.
 */
data class EvalFinding(
    val id: String,
    val method: String,
    val url: String,
    val parameterName: String?,
    val verdict: Verdict,
    val ownerLabel: String? = null,
    val attackerLabel: String? = null,
) {
    companion object {
        /** Из элемента `GET /findings` (`FindingListItem`). */
        fun fromListItem(node: JsonNode, ownerLabel: String? = null, attackerLabel: String? = null) = EvalFinding(
            id = node.get("id").asText(),
            method = node.get("method").asText(),
            url = node.get("url").asText(),
            parameterName = node.get("parameterName")?.takeUnless { it.isNull }?.asText(),
            verdict = Verdict.valueOf(node.get("verdict").asText()),
            ownerLabel = ownerLabel,
            attackerLabel = attackerLabel,
        )
    }
}
