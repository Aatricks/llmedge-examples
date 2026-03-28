package com.example.llmedgeexample.common

import java.util.Locale

fun formatDownloadProgress(
    downloadedBytes: Long,
    totalBytes: Long?,
    prefix: String = "Downloading",
    decimals: Int = 2,
): String {
    val downloadedMb = downloadedBytes / (1024.0 * 1024.0)
    val totalMb = totalBytes?.div(1024.0 * 1024.0)
    val pattern = "%.${decimals}f"
    val downloadedText = String.format(Locale.US, pattern, downloadedMb)
    return if (totalMb != null && totalMb > 0.0) {
        val totalText = String.format(Locale.US, pattern, totalMb)
        "$prefix: $downloadedText MB / $totalText MB"
    } else {
        "$prefix: $downloadedText MB"
    }
}
