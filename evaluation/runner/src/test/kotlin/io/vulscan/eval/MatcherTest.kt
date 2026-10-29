package io.vulscan.eval

import io.vulscan.kit.contracts.Verdict
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MatcherTest {
    private val vulnerableCase = GroundTruthCase("XSS-01", "XSS", "GET /xss/reflect-text q", expectedVulnerable = true)
    private val safeCase = GroundTruthCase("XSS-S01", "XSS", "GET /safe/xss/encoded-text q", expectedVulnerable = false)

    private fun finding(url: String, param: String?, verdict: Verdict) =
        EvalFinding("f", "GET", url, param, verdict)

    @Test
    fun `a case with at least one matching positive finding is a single TP, even with a duplicate`() {
        val findings = listOf(
            finding("http://lab-app:8080/xss/reflect-text", "q", Verdict.LIKELY),
            finding("http://lab-app:8080/xss/reflect-text", "q", Verdict.CONFIRMED), // дубль того же кейса
        )
        val result = Matcher.match(listOf(vulnerableCase), findings, Verdict.LIKELY)
        assertThat(result.tp).isEqualTo(1)
        assertThat(result.fp).isEqualTo(0)
        assertThat(result.fn).isEqualTo(0)
        assertThat(result.duplicates).isEqualTo(1)
    }

    @Test
    fun `a vulnerable case with no matching finding is a FN`() {
        val result = Matcher.match(listOf(vulnerableCase), emptyList(), Verdict.LIKELY)
        assertThat(result.fn).isEqualTo(1)
        assertThat(result.tp).isEqualTo(0)
    }

    @Test
    fun `a safe case with a matching finding is a FP`() {
        val findings = listOf(finding("http://lab-app:8080/safe/xss/encoded-text", "q", Verdict.LIKELY))
        val result = Matcher.match(listOf(safeCase), findings, Verdict.LIKELY)
        assertThat(result.fp).isEqualTo(1)
    }

    @Test
    fun `a safe case with no finding is a true negative and is not counted anywhere`() {
        val result = Matcher.match(listOf(safeCase), emptyList(), Verdict.LIKELY)
        assertThat(result.tp).isEqualTo(0)
        assertThat(result.fp).isEqualTo(0)
        assertThat(result.fn).isEqualTo(0)
    }

    @Test
    fun `a finding matching no case at all is an unmatched FP`() {
        val findings = listOf(finding("http://lab-app:8080/xss/reflect-attr-dq", "q", Verdict.LIKELY))
        val result = Matcher.match(listOf(vulnerableCase), findings, Verdict.LIKELY)
        assertThat(result.fn).isEqualTo(1) // vulnerableCase сам остался без находки
        assertThat(result.fp).isEqualTo(1) // ...а чужая находка не пропала молча
        assertThat(result.unmatchedFindings).hasSize(1)
    }

    @Test
    fun `the threshold drops findings below it`() {
        val findings = listOf(finding("http://lab-app:8080/xss/reflect-text", "q", Verdict.SUSPECTED))
        val result = Matcher.match(listOf(vulnerableCase), findings, Verdict.LIKELY)
        assertThat(result.tp).isEqualTo(0)
        assertThat(result.fn).isEqualTo(1)
    }
}
