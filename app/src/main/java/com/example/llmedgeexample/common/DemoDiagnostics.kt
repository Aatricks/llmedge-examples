package com.example.llmedgeexample.common

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import io.aatricks.llmedge.LLMEdge

private const val BYTES_IN_MB = 1024L * 1024L
private const val MB_PER_GB = 1024L

data class DemoMemorySnapshot(
    val heapUsedMb: Long,
    val heapMaxMb: Long,
    val systemAvailableMb: Long,
    val systemTotalMb: Long,
) {
    val systemUsedMb: Long
        get() = systemTotalMb - systemAvailableMb

    val totalRamGb: Long
        get() = systemTotalMb / MB_PER_GB
}

fun Context.demoMemorySnapshot(): DemoMemorySnapshot {
    val runtime = Runtime.getRuntime()
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    return DemoMemorySnapshot(
        heapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_IN_MB,
        heapMaxMb = runtime.maxMemory() / BYTES_IN_MB,
        systemAvailableMb = memoryInfo.availMem / BYTES_IN_MB,
        systemTotalMb = memoryInfo.totalMem / BYTES_IN_MB,
    )
}

fun Context.availableMemoryMb(): Long = demoMemorySnapshot().systemAvailableMb

fun Context.isLowRamDevice(thresholdGb: Long = 8): Boolean =
    demoMemorySnapshot().totalRamGb < thresholdGb

fun Context.logDemoMemoryState(
    tag: String,
    phase: String,
    includeGpu: Boolean = false,
    logger: (String) -> Unit = { message -> Log.i(tag, message) },
) {
    val snapshot = demoMemorySnapshot()
    logger("=== Memory: $phase ===")
    logger("  Heap: ${snapshot.heapUsedMb}MB / ${snapshot.heapMaxMb}MB max")
    logger("  System: ${snapshot.systemAvailableMb}MB / ${snapshot.systemTotalMb}MB total")
    if (includeGpu) {
        if (isOpenClAvailableCompat()) {
            logger("  OpenCL: available")
        }
        LLMEdge.getVulkanDeviceInfo()?.let { vulkan ->
            logger("  Vulkan: ${vulkan.freeMemoryMB}MB / ${vulkan.totalMemoryMB}MB")
        }
    }
}

fun Context.buildDemoMemorySummary(cpuOnlyOverride: Boolean): String {
    val snapshot = demoMemorySnapshot()
    val gpuStatus = detectGpuBackendStatus()
    return buildString {
        appendLine(
            "System: ${snapshot.systemUsedMb}MB / ${snapshot.systemTotalMb}MB (${snapshot.systemAvailableMb}MB free)"
        )
        appendLine("Heap: ${snapshot.heapUsedMb}MB / ${snapshot.heapMaxMb}MB")
        appendLine("GPU backends: ${gpuStatus.summary()}")
        appendLine("CPU-only override: ${if (cpuOnlyOverride) "ON" else "OFF"}")
        gpuStatus.vulkanInfo?.let { vulkanInfo ->
            appendLine("Vulkan mem: ${vulkanInfo.freeMemoryMB}MB / ${vulkanInfo.totalMemoryMB}MB")
        }
        if (isLowRamDevice()) {
            appendLine("Note: Low RAM device - sequential loading enabled")
        }
    }.trim()
}
