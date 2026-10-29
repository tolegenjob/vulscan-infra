package io.vulscan.eval

import org.yaml.snakeyaml.Yaml
import java.io.File

/** Один случай `ground-truth.yaml` (формат описан в плане, T-1). */
data class GroundTruthCase(
    val id: String,
    val module: String,
    val key: String,
    val expectedVulnerable: Boolean,
    val vulnType: String? = null,
    val variant: String? = null,
    val safeDoubleOf: String? = null,
)

data class GroundTruth(val version: Int, val labAppVersion: String, val cases: List<GroundTruthCase>) {
    fun casesFor(module: String): List<GroundTruthCase> = cases.filter { it.module.equals(module, ignoreCase = true) }

    companion object {
        fun load(file: File): GroundTruth {
            val yaml = Yaml()
            val root = file.inputStream().use {
                @Suppress("UNCHECKED_CAST")
                yaml.load(it) as Map<String, Any?>
            }
            val version = (root["version"] as? Number)?.toInt() ?: 1
            val labAppVersion = root["labAppVersion"] as? String ?: ""
            @Suppress("UNCHECKED_CAST")
            val rawCases = root["cases"] as? List<Map<String, Any?>> ?: emptyList()
            val cases = rawCases.map {
                GroundTruthCase(
                    id = it["id"] as String,
                    module = it["module"] as String,
                    key = it["key"] as String,
                    expectedVulnerable = it["expectedVulnerable"] as Boolean,
                    vulnType = it["vulnType"] as String?,
                    variant = it["variant"] as String?,
                    safeDoubleOf = it["safeDoubleOf"] as String?,
                )
            }
            return GroundTruth(version, labAppVersion, cases)
        }
    }
}
