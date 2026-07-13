package com.example.llmedgeexample.common

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import io.aatricks.llmedge.ComputeBackendAvailability
import io.aatricks.llmedge.DiffusionWorkerMode
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
                    // "Force CPU only" must also cover image/video: on devices whose Vulkan driver
                    // loads but crashes/deadlocks at the first compute dispatch (e.g. some Mali /
                    // PowerVR parts), useVulkan=false is the only escape hatch in IN_PROCESS mode.
                    image =
                        ImageRuntimeConfig(
                            // Run image/video generation in the library's :llmedge_sd worker process
                            // so a native GPU-driver crash/hang is contained and auto-retried on CPU
                            // (hangRecoveryPolicy defaults to RETRY_CPU_THEN_FAIL; the broken backend
                            // is blacklisted + persisted per device). Observed on the Dimensity 7025's
                            // Mali driver, which loads Vulkan then crashes at the first compute dispatch.
                            workerMode = DiffusionWorkerMode.ISOLATED_PROCESS,
                            useVulkan = !isCpuOnlyForced(context),
                            preferPerformanceMode = preferPerformanceMode && !isCpuOnlyForced(context),
                        ),
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
