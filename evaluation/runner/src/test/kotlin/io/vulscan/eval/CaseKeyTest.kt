package io.vulscan.eval

import io.vulscan.kit.contracts.Verdict
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CaseKeyTest {
    @Test
    fun `normalizes scheme and host away, keeps the path template`() {
        val finding = EvalFinding("1", "GET", "http://lab-app:8080/api/orders/{id}", "id", Verdict.LIKELY)
        assertThat(CaseKey.of(finding)).isEqualTo("GET /api/orders/{id} id")
    }

    @Test
    fun `uppercases the method`() {
        val finding = EvalFinding("1", "get", "http://lab-app:8080/xss/reflect-text", "q", Verdict.LIKELY)
        assertThat(CaseKey.of(finding)).isEqualTo("GET /xss/reflect-text q")
    }

    @Test
    fun `uses a dash when there is no parameter`() {
        val finding = EvalFinding("1", "GET", "http://lab-app:8080/api/admin/users", null, Verdict.LIKELY)
        assertThat(CaseKey.of(finding)).isEqualTo("GET /api/admin/users -")
    }

    @Test
    fun `appends owner-greater-than-attacker for IDOR`() {
        val finding = EvalFinding(
            "1", "GET", "http://lab-app:8080/api/orders/{id}", "id", Verdict.CONFIRMED,
            ownerLabel = "alice", attackerLabel = "bob",
        )
        assertThat(CaseKey.of(finding)).isEqualTo("GET /api/orders/{id} id alice->bob")
    }

    @Test
    fun `drops a trailing slash except for the root path`() {
        assertThat(CaseKey.pathOnly("http://lab-app:8080/xss/reflect-text/")).isEqualTo("/xss/reflect-text")
        assertThat(CaseKey.pathOnly("http://lab-app:8080/")).isEqualTo("/")
        assertThat(CaseKey.pathOnly("http://lab-app:8080")).isEqualTo("/")
    }
}
