package com.heartrate.phone.data.persistence

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Export persisted records as CSV/JSON into app-private files.
 */
class HeartRateExportManager(
    private val appContext: Context,
    private val heartRateDao: HeartRateDao
) {
    private val json = Json { prettyPrint = true }

    suspend fun exportCsv(): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val records = heartRateDao.getAll()
            val target = createFile("csv")
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
            target
        }
    }

    suspend fun exportJson(): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val records = heartRateDao.getAll().map { it.toDomain() }
            val target = createFile("json")
            target.writeText(json.encodeToString(records))
            target
        }
    }

    private fun createFile(extension: String): File {
        val dir = File(appContext.filesDir, EXPORT_DIR).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(dir, "heart_rate_export_$stamp.$extension")
    }

    companion object {
        private const val EXPORT_DIR = "exports"
    }
}

