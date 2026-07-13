package com.example.llmedgeexample.common

import android.app.Activity
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Shared log-file sharing for the generation demos (video, image).
 *
 * Surfaces the app-wide [FileLogger] output as a shareable text file so users without logcat
 * access can export generation logs — including failures such as model-resolution errors — for
 * diagnosis.
 */
object GenerationLogs {
    /** Friendly, user-visible location of the current log file (relative to `/Android/...`). */
    fun currentLogPathLabel(): String? =
        FileLogger.getCurrentLogFile()?.substringAfterLast("/Android/")

    /** Build an `ACTION_SEND` intent for the current log file, or null if none exists yet. */
    fun buildShareLogsIntent(activity: Activity): Intent? {
        FileLogger.flush()
        val logFile = FileLogger.getCurrentLogFile()?.let(::File)
        if (logFile == null || !logFile.exists()) {
            return null
        }
        val uri =
            FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                logFile,
            )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "LLMEdge Logs")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
