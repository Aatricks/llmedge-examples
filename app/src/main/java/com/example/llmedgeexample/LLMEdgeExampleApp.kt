package com.example.llmedgeexample

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import io.aatricks.llmedge.LLMEdge

/**
 * Application class for LLMEdge Example app.
 *
 * Handles global application lifecycle events and memory management.
 * Demo activities own and close their own LLMEdge instances.
 */
class LLMEdgeExampleApp : Application() {

    companion object {
        private const val TAG = "LLMEdgeExampleApp"
        private const val BYTES_IN_MB = 1024L * 1024L
        
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
        val runtime = Runtime.getRuntime()
        val heapUsed = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_IN_MB
        val heapMax = runtime.maxMemory() / BYTES_IN_MB
        val heapFree = heapMax - heapUsed

        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val systemAvail = memoryInfo.availMem / BYTES_IN_MB
        val systemTotal = memoryInfo.totalMem / BYTES_IN_MB

        FileLogger.i(TAG, "=== Memory State: $phase ===")
        FileLogger.i(TAG, "  Heap: ${heapUsed}MB used / ${heapMax}MB max (${heapFree}MB free)")
        FileLogger.i(TAG, "  System: ${systemAvail}MB available / ${systemTotal}MB total")
        FileLogger.i(TAG, "  Low memory: ${memoryInfo.lowMemory}")
        
        if (isOpenClAvailableCompat()) {
            FileLogger.i(TAG, "  OpenCL: available")
        }
        LLMEdge.getVulkanDeviceInfo()?.let { vulkan ->
            FileLogger.i(TAG, "  Vulkan: ${vulkan.freeMemoryMB}MB free / ${vulkan.totalMemoryMB}MB total")
        }
    }
    
    /**
     * Check if device has low memory (less than 8GB total RAM).
     */
    fun isLowMemoryDevice(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalRamGB = memInfo.totalMem / (1024L * 1024L * 1024L)
        return totalRamGB < 8
    }

    /**
     * Get available system memory in MB.
     */
    fun getAvailableMemoryMB(): Long {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.availMem / BYTES_IN_MB
    }
}
