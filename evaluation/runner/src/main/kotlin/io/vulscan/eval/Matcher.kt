package io.vulscan.eval

import io.vulscan.kit.contracts.Verdict

enum class Outcome { TP, FP, FN }

data class CaseOutcome(val caseId: String, val outcome: Outcome)

/**
 * Итог сопоставления findings с ground truth для одного модуля/порога (план, E-1.2).
 * Истинные отрицания (`expectedVulnerable=false` и findings нет) не попадают ни в один счётчик —
 * это ожидаемое поведение, не TN-метрика.
 */
data class MatchResult(
    val outcomes: List<CaseOutcome>,
    val unmatchedFindings: List<EvalFinding>,
    val duplicates: Int,
) {
    val tp: Int get() = outcomes.count { it.outcome == Outcome.TP }
    val fp: Int get() = outcomes.count { it.outcome == Outcome.FP } + unmatchedFindings.size
    val fn: Int get() = outcomes.count { it.outcome == Outcome.FN }
}

object Matcher {
    /**
     * [threshold] отбрасывает findings со слабым вердиктом (`verdict.ordinal < threshold.ordinal`,
     * порядок объявления `Verdict` — SUSPECTED < LIKELY < CONFIRMED, см. kit). Дубли на один
     * [CaseKey] схлопываются в один TP/FP (план требует fingerprint без дублей; здесь — defensive,
     * считаем дубль отдельно в [MatchResult.duplicates]).
     */
    fun match(cases: List<GroundTruthCase>, findings: List<EvalFinding>, threshold: Verdict): MatchResult {
        val positive = findings.filter { it.verdict.ordinal >= threshold.ordinal }
        val byKey = positive.groupBy { CaseKey.of(it) }
        val caseKeys = cases.map { it.key }.toSet()

        var duplicates = 0
        val outcomes = mutableListOf<CaseOutcome>()
        for (case in cases) {
            val matching = byKey[case.key] ?: emptyList()
            if (matching.size > 1) duplicates += matching.size - 1
            val hasPositive = matching.isNotEmpty()
            val outcome = when {
                case.expectedVulnerable && hasPositive -> Outcome.TP
                case.expectedVulnerable && !hasPositive -> Outcome.FN
                !case.expectedVulnerable && hasPositive -> Outcome.FP
                else -> null // expectedVulnerable=false и findings нет: true negative, не считается
            }
            if (outcome != null) outcomes += CaseOutcome(case.id, outcome)
        }
        val unmatched = positive.filter { CaseKey.of(it) !in caseKeys }
        return MatchResult(outcomes, unmatched, duplicates)
    }
}
