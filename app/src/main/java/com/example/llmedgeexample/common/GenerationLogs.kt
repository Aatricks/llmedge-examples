package com.example.llmedgeexample.common

import android.app.Activity
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Shared log-file sharing for the generation demos (video, image).
 *
 * Surfaces the app-wide [FileLogger] output as a shareable file so users without logcat access can
 * export generation logs — including failures such as model-resolution errors — for diagnosis.
 *
 * A crash that kills the process closes the current log and starts a fresh one on the next launch,
 * so a single-file share would miss it. When more than one session log exists, this bundles **all**
 * recent logs into a zip, so the crashed session's trail (written synchronously and fsync'd by
 * [FileLogger]) is still included after the app is reopened.
 */
object GenerationLogs {
    private const val LOG_BUNDLE_NAME = "llmedge_logs.zip"

    /** Friendly, user-visible location of the current log file (relative to `/Android/...`). */
    fun currentLogPathLabel(): String? =
        FileLogger.getCurrentLogFile()?.substringAfterLast("/Android/")

    /**
     * Build an `ACTION_SEND` intent for the app logs, or null if none exist yet.
     *
     * Shares a single `.log` when only one session exists; otherwise a zip of every recent session
     * (so a crash captured before a restart is still exported).
     */
    fun buildShareLogsIntent(activity: Activity): Intent? {
        FileLogger.flush()
        val dir = FileLogger.getLogDirectory()?.let(::File)
        val logs =
            dir
                ?.listFiles { f -> f.isFile && f.name.endsWith(".log") }
                ?.sortedByDescending(File::lastModified)
                .orEmpty()

        val (shareFile, mime) =
            when {
                logs.size > 1 && dir != null -> zipLogs(dir, logs) to "application/zip"
                logs.isNotEmpty() -> logs.first() to "text/plain"
                else -> FileLogger.getCurrentLogFile()?.let(::File) to "text/plain"
            }

        if (shareFile == null || !shareFile.exists()) {
            return null
        }
        val uri =
            FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                shareFile,
            )
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "LLMEdge Logs")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Bundle every session log into one zip (overwritten each share). Returns null on failure. */
    private fun zipLogs(dir: File, logs: List<File>): File? {
        val bundle = File(dir, LOG_BUNDLE_NAME)
        return try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(bundle))).use { zos ->
                val buffer = ByteArray(64 * 1024)
                for (log in logs) {
                    if (!log.isFile) continue
                    zos.putNextEntry(ZipEntry(log.name))
                    FileInputStream(log).use { input ->
                        var read = input.read(buffer)
                        while (read >= 0) {
                            zos.write(buffer, 0, read)
                            read = input.read(buffer)
                        }
                    }
                    zos.closeEntry()
                }
            }
            bundle
        } catch (_: Exception) {
            null
        }
    }
}
