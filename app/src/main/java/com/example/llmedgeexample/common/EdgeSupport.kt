package com.example.llmedgeexample.common

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import io.aatricks.llmedge.ComputeBackendAvailability
import io.aatricks.llmedge.ImageRuntimeConfig
import io.aatricks.llmedge.LLMEdge
import io.aatricks.llmedge.LLMEdgeConfig
import io.aatricks.llmedge.TextRuntimeConfig
import io.aatricks.llmedge.VulkanDeviceInfo
import io.aatricks.llmedge.lifecycle.LLMEdgeLifecycle
import kotlinx.coroutines.CoroutineScope

private const val EDGE_PREFS_NAME = "llmedge_example_prefs"
private const val PREF_FORCE_CPU_ONLY = "force_cpu_only"

fun bindEdge(
    owner: LifecycleOwner,
    context: Context,
    scope: CoroutineScope,
    preferPerformanceMode: Boolean = false,
): LLMEdge =
    LLMEdgeLifecycle.bind(
        owner,
        LLMEdge.create(
            context = context,
            scope = scope,
            config =
                LLMEdgeConfig(
                    text = TextRuntimeConfig(useVulkan = !isCpuOnlyForced(context)),
                    image = ImageRuntimeConfig(preferPerformanceMode = preferPerformanceMode && !isCpuOnlyForced(context)),
                ),
        ),
    )

fun isCpuOnlyForced(context: Context): Boolean =
    context
        .applicationContext
        .getSharedPreferences(EDGE_PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(PREF_FORCE_CPU_ONLY, false)

fun setCpuOnlyForced(context: Context, enabled: Boolean) {
    context
        .applicationContext
        .getSharedPreferences(EDGE_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(PREF_FORCE_CPU_ONLY, enabled)
        .apply()
}

data class GpuBackendStatus(
    val text: ComputeBackendAvailability,
    val speech: ComputeBackendAvailability,
    val image: ComputeBackendAvailability,
    val vision: ComputeBackendAvailability,
) {
    val openClAvailable: Boolean
        get() = text.openClAvailable || speech.openClAvailable || image.openClAvailable || vision.openClAvailable

    val vulkanAvailable: Boolean
        get() = text.vulkanAvailable || speech.vulkanAvailable || image.vulkanAvailable || vision.vulkanAvailable

    val vulkanInfo: VulkanDeviceInfo?
        get() = image.vulkanDeviceInfo
}

fun detectGpuBackendStatus(): GpuBackendStatus =
    GpuBackendStatus(
        text = LLMEdge.getTextBackendAvailability(),
        speech = LLMEdge.getSpeechBackendAvailability(),
        image = LLMEdge.getImageBackendAvailability(),
        vision = LLMEdge.getVisionBackendAvailability(),
    )

fun GpuBackendStatus.summary(): String {
    val names = ArrayList<String>(2)
    if (openClAvailable) {
        names += "OpenCL"
    }
    if (vulkanAvailable) {
        names += "Vulkan"
    }
    return if (names.isEmpty()) "CPU only" else "${names.joinToString(", ")} (varies by feature)"
}
