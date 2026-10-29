package io.vulscan.eval

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Таблицы из плана, E-1.3. */
class MetricsTest {
    @Test
    fun `tp=8 fp=2 fn=0 gives precision 0_8 and recall 1_0`() {
        val m = Metrics(tp = 8, fp = 2, fn = 0, duplicates = 0)
        assertThat(m.precision.value).isEqualTo(0.8)
        assertThat(m.recall.value).isEqualTo(1.0)
    }

    @Test
    fun `tp=0 fp=0 gives precision N over A`() {
        val m = Metrics(tp = 0, fp = 0, fn = 5, duplicates = 0)
        assertThat(m.precision.value).isNull()
        assertThat(m.precision.toString()).isEqualTo("N/A")
    }

    @Test
    fun `tp=0 fn=5 gives recall 0_0, not N over A`() {
        val m = Metrics(tp = 0, fp = 0, fn = 5, duplicates = 0)
        assertThat(m.recall.value).isEqualTo(0.0)
    }

    @Test
    fun `tp+fn=0 gives recall N over A`() {
        val m = Metrics(tp = 0, fp = 3, fn = 0, duplicates = 0)
        assertThat(m.recall.value).isNull()
        assertThat(m.recall.toString()).isEqualTo("N/A")
    }

    @Test
    fun `micro-average sums tp fp fn across modules without averaging ratios`() {
        val xss = Metrics(tp = 4, fp = 1, fn = 0, duplicates = 0)
        val sqli = Metrics(tp = 2, fp = 0, fn = 1, duplicates = 0)
        val micro = Metrics.micro(listOf(xss, sqli))
        assertThat(micro.tp).isEqualTo(6)
        assertThat(micro.fp).isEqualTo(1)
        assertThat(micro.fn).isEqualTo(1)
    }
}
