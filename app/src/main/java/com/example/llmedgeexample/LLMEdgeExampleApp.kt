package com.example.llmedgeexample

import android.app.Application

/**
 * Application class for LLMEdge Example app.
 *
 * Handles global application lifecycle events and memory management.
 * Demo activities own and close their own LLMEdge instances.
 */
class LLMEdgeExampleApp : Application() {

    companion object {
        private const val TAG = "LLMEdgeExampleApp"

        @Volatile
        private var instance: LLMEdgeExampleApp? = null

        fun getInstance(): LLMEdgeExampleApp? = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize file logger for users without logcat access
        FileLogger.init(this)
        FileLogger.separator("Application Starting")
        FileLogger.i(TAG, "Log file: ${FileLogger.getCurrentLogFile()}")
        
        logMemoryState("Application started")
        
        val gpuStatus = detectGpuBackendStatus()
        if (gpuStatus.openClAvailable || gpuStatus.vulkanAvailable) {
            FileLogger.i(TAG, "GPU backends available: ${gpuStatus.summary()}")
            gpuStatus.vulkanInfo?.let { vulkanInfo ->
                FileLogger.i(
                    TAG,
                    "Vulkan available: ${vulkanInfo.deviceCount} device(s), " +
                        "${vulkanInfo.freeMemoryMB}MB free / ${vulkanInfo.totalMemoryMB}MB total",
                )
            }
        } else {
            FileLogger.w(TAG, "GPU backends unavailable - CPU fallback only")
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        val levelName = TrimMemorySupport.describe(level)

        FileLogger.i(TAG, "onTrimMemory: level=$levelName")
        logMemoryState("Before memory cleanup")

        when {
            TrimMemorySupport.isCritical(level) -> {
                FileLogger.w(TAG, "Critical memory pressure - active demo screens should cancel their own work")
            }
            TrimMemorySupport.isBackgroundPressure(level) -> {
                // App is backgrounded or moderate pressure - allow GC to run
                FileLogger.i(TAG, "Moderate memory pressure - allowing GC")
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        FileLogger.w(TAG, "onLowMemory - critical memory situation")
        logMemoryState("Low memory callback")
    }

    /**
     * Log detailed memory state for debugging.
     */
    fun logMemoryState(phase: String) {
        val snapshot = demoMemorySnapshot()
        logDemoMemoryState(TAG, phase, includeGpu = true) { FileLogger.i(TAG, it) }
        FileLogger.i(TAG, "  Heap free: ${snapshot.heapMaxMb - snapshot.heapUsedMb}MB")
    }
    
    /**
     * Check if device has low memory (less than 8GB total RAM).
     */
    fun isLowMemoryDevice(): Boolean = isLowRamDevice()

    /**
     * Get available system memory in MB.
     */
    fun getAvailableMemoryMB(): Long = availableMemoryMb()
}
