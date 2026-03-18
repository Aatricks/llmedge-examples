package com.example.llmedgeexample

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import io.aatricks.llmedge.LLMEdge
import io.aatricks.llmedge.LLMEdgeConfig
import io.aatricks.llmedge.VulkanDeviceInfo
import io.aatricks.llmedge.lifecycle.LLMEdgeLifecycle
import kotlinx.coroutines.CoroutineScope

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
            config = LLMEdgeConfig(preferPerformanceMode = preferPerformanceMode),
        ),
    )

fun isOpenClAvailableCompat(): Boolean =
    runCatching {
        val method = LLMEdge::class.java.getMethod("isOpenClAvailable")
        (method.invoke(null) as? Boolean) == true
    }.getOrDefault(false)

data class GpuBackendStatus(
    val openClAvailable: Boolean,
    val vulkanAvailable: Boolean,
    val vulkanInfo: VulkanDeviceInfo?,
)

fun detectGpuBackendStatus(): GpuBackendStatus {
    val vulkanInfo = LLMEdge.getVulkanDeviceInfo()
    return GpuBackendStatus(
        openClAvailable = isOpenClAvailableCompat(),
        vulkanAvailable = vulkanInfo != null || LLMEdge.isVulkanAvailable(),
        vulkanInfo = vulkanInfo,
    )
}

fun GpuBackendStatus.summary(): String {
    val names = ArrayList<String>(2)
    if (openClAvailable) {
        names += "OpenCL"
    }
    if (vulkanAvailable) {
        names += "Vulkan"
    }
    return if (names.isEmpty()) "CPU only" else names.joinToString(", ")
}
