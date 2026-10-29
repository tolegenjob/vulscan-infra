package io.vulscan.eval

/** `null` печатается как `N/A` — нулевой знаменатель (план, E-1.3): ни 0, ни 1. */
data class MetricValue(val value: Double?) {
    override fun toString(): String = value?.let { "%.3f".format(it) } ?: "N/A"
}

data class Metrics(val tp: Int, val fp: Int, val fn: Int, val duplicates: Int) {
    val precision: MetricValue get() = if (tp + fp == 0) MetricValue(null) else MetricValue(tp.toDouble() / (tp + fp))
    val recall: MetricValue get() = if (tp + fn == 0) MetricValue(null) else MetricValue(tp.toDouble() / (tp + fn))

    companion object {
        fun of(result: MatchResult) = Metrics(result.tp, result.fp, result.fn, result.duplicates)

        /** "Micro-average" по нескольким модулям (план, E-1.3) — суммы TP/FP/FN, не усреднённые P/R. */
        fun micro(perModule: Collection<Metrics>) = Metrics(
            tp = perModule.sumOf { it.tp },
            fp = perModule.sumOf { it.fp },
            fn = perModule.sumOf { it.fn },
            duplicates = perModule.sumOf { it.duplicates },
        )
    }
}
