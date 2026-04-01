package com.heartrate.phone.data.persistence

import android.content.Context
import com.heartrate.shared.data.model.HeartRateData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Export persisted records into app-private files and keep lightweight metadata
 * so the UI can show when each export covers.
 */
class HeartRateExportManager(
    private val appContext: Context,
    private val heartRateDao: HeartRateDao
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun exportCsv(): Result<HeartRateExportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val records = heartRateDao.getAll()
            val target = createDataFile("csv")
            target.bufferedWriter().use { writer ->
                writer.appendLine("timestamp,heartRate,deviceId,batteryLevel,signalQuality,synced")
                records.forEach { row ->
                    writer.appendLine(
                        listOf(
                            row.timestamp,
                            row.heartRate,
                            row.deviceId,
                            row.batteryLevel?.toString().orEmpty(),
                            row.signalQuality?.toString().orEmpty(),
                            row.synced
                        ).joinToString(",")
                    )
                }
            }
            writeMetadata(records, target, format = ExportFormat.CSV)
        }
    }

    suspend fun exportJson(): Result<HeartRateExportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val records = heartRateDao.getAll()
            val target = createDataFile("json")
            target.writeText(json.encodeToString(records.map { it.toDomain() }))
            writeMetadata(records, target, format = ExportFormat.JSON)
        }
    }

    suspend fun exportChartSvg(): Result<HeartRateExportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val records = heartRateDao.getAll()
            val chartRecords = records.takeLast(CHART_SAMPLE_LIMIT)
            val target = createDataFile("svg")
            target.writeText(buildSvg(chartRecords.map { it.toDomain() }))
            writeMetadata(chartRecords, target, format = ExportFormat.SVG)
        }
    }

    suspend fun listExportHistory(): List<HeartRateExportMetadata> = withContext(Dispatchers.IO) {
        metadataDir().listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .mapNotNull { file ->
                runCatching {
                    json.decodeFromString<HeartRateExportMetadata>(file.readText())
                }.getOrNull()
            }
            .sortedByDescending { it.generatedAt }
    }

    private fun createDataFile(extension: String): File {
        val stamp = FILE_TIMESTAMP.format(Date())
        return File(exportDir().apply { mkdirs() }, "heart_rate_export_$stamp.$extension")
    }

    private fun writeMetadata(
        records: List<HeartRateEntity>,
        target: File,
        format: ExportFormat
    ): HeartRateExportResult {
        val rangeStart = records.firstOrNull()?.timestamp
        val rangeEnd = records.lastOrNull()?.timestamp
        val metadata = HeartRateExportMetadata(
            fileName = target.name,
            filePath = target.absolutePath,
            format = format,
            generatedAt = System.currentTimeMillis(),
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            sampleCount = records.size
        )
        val metadataFile = File(metadataDir().apply { mkdirs() }, "${target.nameWithoutExtension}.meta.json")
        metadataFile.writeText(json.encodeToString(metadata))
        return HeartRateExportResult(file = target, metadata = metadata)
    }

    private fun buildSvg(records: List<HeartRateData>): String {
        val width = 1200f
        val height = 720f
        val padding = 72f
        val innerWidth = width - padding * 2
        val innerHeight = height - padding * 2
        val values = records.map { it.heartRate.coerceAtLeast(0) }
        val minValue = (values.minOrNull() ?: 50).coerceAtMost(90)
        val maxValue = max((values.maxOrNull() ?: 120), minValue + 10)
        val points = records.mapIndexed { index, record ->
            val x = if (records.size <= 1) {
                padding + innerWidth / 2f
            } else {
                padding + innerWidth * (index / (records.lastIndex).toFloat())
            }
            val ratio = (record.heartRate - minValue).toFloat() / (maxValue - minValue).toFloat()
            val y = padding + innerHeight - ratio.coerceIn(0f, 1f) * innerHeight
            x to y
        }
        val path = if (points.isEmpty()) "" else points.joinToString(" ") { (x, y) -> "${x.format()} ${y.format()}" }
        val latest = values.lastOrNull()
        val subtitle = when {
            latest == null -> "No heart rate records yet"
            else -> "Latest BPM $latest · Samples ${records.size}"
        }
        return buildString {
            appendLine("""<svg xmlns="http://www.w3.org/2000/svg" width="${width.toInt()}" height="${height.toInt()}" viewBox="0 0 ${width.toInt()} ${height.toInt()}">""")
            appendLine("""  <defs>""")
            appendLine("""    <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">""")
            appendLine("""      <stop offset="0%" stop-color="#FF9A45"/>""")
            appendLine("""      <stop offset="100%" stop-color="#FF6A3D"/>""")
            appendLine("""    </linearGradient>""")
            appendLine("""    <linearGradient id="line" x1="0%" y1="0%" x2="100%" y2="0%">""")
            appendLine("""      <stop offset="0%" stop-color="#FFFFFF"/>""")
            appendLine("""      <stop offset="100%" stop-color="#FFE6D4"/>""")
            appendLine("""    </linearGradient>""")
            appendLine("""  </defs>""")
            appendLine("""  <rect width="100%" height="100%" rx="40" fill="#FFF7F2"/>""")
            appendLine("""  <rect x="40" y="40" width="${(width - 80).toInt()}" height="${(height - 80).toInt()}" rx="32" fill="url(#bg)"/>""")
            appendLine("""  <text x="88" y="118" font-size="44" font-family="sans-serif" font-weight="700" fill="#FFFFFF">Heart Rate Trend</text>""")
            appendLine("""  <text x="88" y="164" font-size="26" font-family="sans-serif" fill="#FFE9DB">$subtitle</text>""")
            appendLine("""  <line x1="$padding" y1="${height - padding}" x2="${width - padding}" y2="${height - padding}" stroke="#FFD3BF" stroke-width="2"/>""")
            appendLine("""  <line x1="$padding" y1="$padding" x2="$padding" y2="${height - padding}" stroke="#FFD3BF" stroke-width="2"/>""")
            appendLine("""  <text x="$padding" y="${height - 28}" font-size="22" font-family="sans-serif" fill="#FFE9DB">Start</text>""")
            appendLine("""  <text x="${width - 148}" y="${height - 28}" font-size="22" font-family="sans-serif" fill="#FFE9DB">Latest</text>""")
            appendLine("""  <text x="22" y="${padding + 10}" font-size="22" font-family="sans-serif" fill="#FFE9DB">${maxValue}bpm</text>""")
            appendLine("""  <text x="22" y="${height - padding + 8}" font-size="22" font-family="sans-serif" fill="#FFE9DB">${minValue}bpm</text>""")
            if (points.size >= 2) {
                appendLine("""  <polyline fill="none" stroke="url(#line)" stroke-width="8" stroke-linecap="round" stroke-linejoin="round" points="$path"/>""")
            } else if (points.size == 1) {
                val (x, y) = points.first()
                appendLine("""  <circle cx="${x.format()}" cy="${y.format()}" r="10" fill="#FFFFFF"/>""")
            }
            if (points.isNotEmpty()) {
                val (x, y) = points.last()
                appendLine("""  <circle cx="${x.format()}" cy="${y.format()}" r="12" fill="#FFFFFF"/>""")
                appendLine("""  <circle cx="${x.format()}" cy="${y.format()}" r="24" fill="#FFFFFF" fill-opacity="0.25"/>""")
            }
            appendLine("""</svg>""")
        }
    }

    private fun exportDir(): File = File(appContext.filesDir, EXPORT_DIR)

    private fun metadataDir(): File = File(exportDir(), METADATA_DIR)

    private fun Float.format(): String = String.format(Locale.US, "%.2f", this)

    companion object {
        private const val EXPORT_DIR = "exports"
        private const val METADATA_DIR = ".meta"
        private const val CHART_SAMPLE_LIMIT = 240
        private val FILE_TIMESTAMP = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    }
}

@Serializable
data class HeartRateExportMetadata(
    val fileName: String,
    val filePath: String,
    val format: ExportFormat,
    val generatedAt: Long,
    val rangeStart: Long?,
    val rangeEnd: Long?,
    val sampleCount: Int
)

data class HeartRateExportResult(
    val file: File,
    val metadata: HeartRateExportMetadata
)

@Serializable
enum class ExportFormat {
    CSV,
    JSON,
    SVG
}
