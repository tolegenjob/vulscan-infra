package io.vulscan.eval

import java.io.File
import java.security.MessageDigest
import java.time.Instant

/**
 * Воспроизводимость запуска (план, E-1.5). `repoCommitSha`/`repoDirty` — только для репозитория,
 * из которого запускается сам runner (`vulscan-infra`); SHA остальных пяти репозиториев этот
 * инструмент не собирает (нет единого checkout, см. `evaluation/README.md`) — это задокументированное
 * ограничение, а не забытый пункт.
 */
data class RunManifest(
    val timestampUtc: String,
    val repoCommitSha: String?,
    val repoDirty: Boolean?,
    val labAppVersion: String,
    val groundTruthSha256: String,
    val threshold: String,
    val repeats: Int,
    val osName: String,
    val availableProcessors: Int,
    val maxMemoryBytes: Long,
) {
    companion object {
        fun capture(groundTruthFile: File, threshold: String, repeats: Int, repoDir: File): RunManifest {
            val digest = MessageDigest.getInstance("SHA-256").digest(groundTruthFile.readBytes())
            val sha256 = digest.joinToString("") { "%02x".format(it) }
            val commit = gitOutput(repoDir, "rev-parse", "HEAD")
            val dirty = gitOutput(repoDir, "status", "--porcelain")?.isNotBlank()
            return RunManifest(
                timestampUtc = Instant.now().toString(),
                repoCommitSha = commit,
                repoDirty = dirty,
                labAppVersion = "", // заполняется вызывающей стороной из GroundTruth.labAppVersion
                groundTruthSha256 = sha256,
                threshold = threshold,
                repeats = repeats,
                osName = System.getProperty("os.name") ?: "unknown",
                availableProcessors = Runtime.getRuntime().availableProcessors(),
                maxMemoryBytes = Runtime.getRuntime().maxMemory(),
            )
        }

        private fun gitOutput(dir: File, vararg args: String): String? = runCatching {
            val process = ProcessBuilder(listOf("git") + args).directory(dir).redirectErrorStream(true).start()
            val text = process.inputStream.bufferedReader().readText().trim()
            if (process.waitFor() == 0) text else null
        }.getOrNull()
    }
}
