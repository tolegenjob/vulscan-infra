package io.vulscan.eval

import java.io.File

data class RepeatResult(val repeatIndex: Int, val metrics: Metrics, val durationMs: Long, val requestsSent: Int)
data class ModuleReport(val module: String, val repeats: List<RepeatResult>)

object ReportWriter {
    fun writeCsv(file: File, reports: List<ModuleReport>) {
        val lines = mutableListOf("module,repeat,tp,fp,fn,duplicates,precision,recall,durationMs,requestsSent")
        for (report in reports) {
            for (r in report.repeats) {
                lines += listOf(
                    report.module, r.repeatIndex, r.metrics.tp, r.metrics.fp, r.metrics.fn, r.metrics.duplicates,
                    r.metrics.precision, r.metrics.recall, r.durationMs, r.requestsSent,
                ).joinToString(",")
            }
        }
        file.writeText(lines.joinToString("\n") + "\n")
    }

    /**
     * Таблица "по модулям" (план, E-1.3): строка модуля — метрики первого повтора (репрезентативно;
     * полная таблица по всем повторам — ниже, по ней же проверяется детерминизм, план E-2).
     * Время/запросы — медиана и min–max по всем повторам (план, E-1.4). `micro` печатается только
     * с подписью "micro-average, модули разного размера" (план требует эту подпись явно).
     */
    fun writeMarkdown(file: File, reports: List<ModuleReport>, manifest: RunManifest) {
        val sb = StringBuilder()
        sb.appendLine("# Отчёт оценки")
        sb.appendLine()
        sb.appendLine("- Время (UTC): ${manifest.timestampUtc}")
        sb.appendLine("- Коммит runner (vulscan-infra): ${manifest.repoCommitSha ?: "неизвестен"}${if (manifest.repoDirty == true) " (есть незакоммиченные изменения!)" else ""}")
        sb.appendLine("- lab-app: ${manifest.labAppVersion}")
        sb.appendLine("- ground-truth.yaml sha256: ${manifest.groundTruthSha256}")
        sb.appendLine("- Порог (threshold): ${manifest.threshold}")
        sb.appendLine("- Повторов: ${manifest.repeats}")
        sb.appendLine("- Хост: ${manifest.osName}, CPU=${manifest.availableProcessors}, maxMemory=${manifest.maxMemoryBytes / (1024 * 1024)}MiB")
        sb.appendLine()

        sb.appendLine("## По модулям (первый повтор; время/запросы — медиана, min–max по всем повторам)")
        sb.appendLine()
        sb.appendLine("| Модуль | TP | FP | FN | Precision | Recall | Duplicates | Длительность (мс) | Запросов |")
        sb.appendLine("|---|---|---|---|---|---|---|---|---|")
        for (report in reports) {
            val first = report.repeats.first()
            val durations = report.repeats.map { it.durationMs }
            val requests = report.repeats.map { it.requestsSent }
            sb.appendLine(
                "| ${report.module} | ${first.metrics.tp} | ${first.metrics.fp} | ${first.metrics.fn} | " +
                    "${first.metrics.precision} | ${first.metrics.recall} | ${first.metrics.duplicates} | " +
                    "${medianMinMax(durations)} | ${medianMinMax(requests.map { it.toLong() })} |",
            )
        }
        if (reports.size > 1) {
            val micro = Metrics.micro(reports.map { it.repeats.first().metrics })
            sb.appendLine(
                "| **micro-average, модули разного размера** | ${micro.tp} | ${micro.fp} | ${micro.fn} | " +
                    "${micro.precision} | ${micro.recall} | ${micro.duplicates} | — | — |",
            )
        }
        sb.appendLine()

        sb.appendLine("## Все повторы (проверка детерминизма: TP/FP/FN должны совпадать между повторами)")
        sb.appendLine()
        sb.appendLine("| Модуль | Повтор | TP | FP | FN | Precision | Recall | Duplicates | Длительность (мс) | Запросов |")
        sb.appendLine("|---|---|---|---|---|---|---|---|---|---|")
        for (report in reports) {
            for (r in report.repeats) {
                sb.appendLine(
                    "| ${report.module} | ${r.repeatIndex} | ${r.metrics.tp} | ${r.metrics.fp} | ${r.metrics.fn} | " +
                        "${r.metrics.precision} | ${r.metrics.recall} | ${r.metrics.duplicates} | ${r.durationMs} | ${r.requestsSent} |",
                )
            }
            val distinctOutcomes = report.repeats.map { Triple(it.metrics.tp, it.metrics.fp, it.metrics.fn) }.distinct()
            if (distinctOutcomes.size > 1) {
                sb.appendLine()
                sb.appendLine("> **${report.module}: нестабильность между повторами** — TP/FP/FN отличаются (${distinctOutcomes.size} разных исхода). Зафиксировать в RESULTS.md (план, E-2).")
                sb.appendLine()
            }
        }
        file.writeText(sb.toString())
    }

    private fun medianMinMax(values: List<Long>): String {
        if (values.isEmpty()) return "—"
        val sorted = values.sorted()
        val median = sorted[sorted.size / 2]
        return "$median (${sorted.first()}–${sorted.last()})"
    }
}
