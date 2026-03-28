package com.example.llmedgeexample.common

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * File-based logger for users without access to logcat.
 * 
 * Logs are written synchronously to ensure they survive crashes.
 */
object FileLogger {
    private const val TAG = "FileLogger"
    private const val LOG_DIR = "logs"
    private const val MAX_LOG_FILES = 20
    private const val MAX_LOG_SIZE_BYTES = 10 * 1024 * 1024 // 10MB per file
    
    private var logFile: File? = null
    private var logDir: File? = null
    private val initialized = AtomicBoolean(false)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    
    /**
     * Initialize the file logger. Call this in Application.onCreate()
     */
    fun init(context: Context) {
        if (initialized.getAndSet(true)) return
        
        try {
            // Use external files dir so users can access logs without root
            logDir = File(context.getExternalFilesDir(null), LOG_DIR).apply {
                if (!exists()) mkdirs()
            }
            
            // Create new log file with timestamp
            val timestamp = fileNameFormat.format(Date())
            logFile = File(logDir, "llmedge_$timestamp.log")
            
            // Write header
            val header = buildString {
                appendLine("=" .repeat(60))
                appendLine("LLMEdge Example App Log")
                appendLine("Started: ${dateFormat.format(Date())}")
                appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
                appendLine("=" .repeat(60))
                appendLine()
            }
            logFile?.writeText(header)
            
            // Setup uncaught exception handler to flush logs on crash
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                // Log final memory state before dying
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                if (am != null) {
                    val mi = ActivityManager.MemoryInfo()
                    am.getMemoryInfo(mi)
                    e(TAG, "FATAL EXCEPTION in thread ${thread.name}. Memory: Avail=${mi.availMem/1024/1024}MB, Low=${mi.lowMemory}", throwable)
                } else {
                    e(TAG, "FATAL EXCEPTION in thread ${thread.name}", throwable)
                }
                defaultHandler?.uncaughtException(thread, throwable)
            }
            
            // Cleanup old log files
            cleanupOldLogs()
            
            Log.i(TAG, "File logging initialized: ${logFile?.absolutePath}")
            i(TAG, "File logging initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize file logger", e)
        }
    }
    
    /**
     * Get the path to the logs directory for sharing with users.
     */
    fun getLogDirectory(): String? = logDir?.absolutePath
    
    /**
     * Get the current log file path.
     */
    fun getCurrentLogFile(): String? = logFile?.absolutePath
    
    /**
     * Log at INFO level.
     */
    fun i(tag: String, message: String) {
        log("I", tag, message)
        Log.i(tag, message)
    }
    
    /**
     * Log at DEBUG level.
     */
    fun d(tag: String, message: String) {
        log("D", tag, message)
        Log.d(tag, message)
    }
    
    /**
     * Log at WARNING level.
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log("W", tag, message, throwable)
        if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
    }
    
    /**
     * Log at ERROR level.
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log("E", tag, message, throwable)
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }
    
    /**
     * Log at VERBOSE level.
     */
    fun v(tag: String, message: String) {
        log("V", tag, message)
        Log.v(tag, message)
    }
    
    @Synchronized
    private fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
        if (!initialized.get()) return
        
        val timestamp = dateFormat.format(Date())
        val logLine = buildString {
            append("$timestamp $level/$tag: $message")
            if (throwable != null) {
                appendLine()
                append(throwable.stackTraceToString())
            }
            appendLine()
        }
        
        try {
            val file = logFile ?: return
            
            // Check if we need to rotate (best effort)
            if (file.length() > MAX_LOG_SIZE_BYTES) {
                val newTimestamp = fileNameFormat.format(Date())
                logFile = File(logDir, "llmedge_$newTimestamp.log")
                logFile?.writeText("Log rotated at $timestamp\n")
            }
            
            FileOutputStream(logFile, true).use { fos ->
                fos.write(logLine.toByteArray())
                fos.flush()
                // Force sync to hardware to ensure data survives native crash
                try {
                    fos.fd.sync()
                } catch (e: Exception) {
                    // ignore sync errors on some devices
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log", e)
        }
    }
    
    /**
     * Force flush pending logs. No-op since we write synchronously.
     */
    fun flush() {
        // Already synchronous
    }
    
    private fun cleanupOldLogs() {
        try {
            val files = logDir?.listFiles { file -> file.name.endsWith(".log") } 
                ?.sortedByDescending { it.lastModified() } 
                ?: return
            
            // Keep only the most recent files
            files.drop(MAX_LOG_FILES).forEach { file ->
                file.delete()
                Log.d(TAG, "Deleted old log: ${file.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup old logs", e)
        }
    }
    
    /**
     * Log a separator line for visual clarity.
     */
    fun separator(title: String = "") {
        val line = if (title.isBlank()) {
            "-".repeat(60)
        } else {
            "--- $title ${"-".repeat(maxOf(0, 55 - title.length))}"
        }
        i(TAG, line)
    }
}
