package com.example.llmedgeexample.demo.image

import android.graphics.Bitmap
import com.example.llmedgeexample.common.FileLogger
import io.aatricks.llmedge.LLMEdge
import io.aatricks.llmedge.image.ImageGenerationRequest
import io.aatricks.llmedge.image.diffusion.GenerationMetrics
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class ImageGenerationCancellationReason(
    val logLabel: String,
    val statusLabel: String,
) {
    USER_CANCEL(logLabel = "USER_CANCEL", statusLabel = "user cancelled"),
    SCREEN_LEFT(logLabel = "SCREEN_LEFT", statusLabel = "screen left"),
    LOW_MEMORY(logLabel = "LOW_MEMORY", statusLabel = "low memory"),
    ERROR(logLabel = "ERROR", statusLabel = "error"),
}

internal data class ImageGenerationConfig(
    val request: ImageGenerationRequest,
    val loraRequested: Boolean = false,
)

internal data class ImageGenerationResult(
    val requestId: Long,
    val bitmap: Bitmap,
    val metrics: GenerationMetrics?,
)

internal data class ImageGenerationCallbacks(
    val onProgress: (percent: Int, status: String) -> Unit,
    val onCompleted: (ImageGenerationResult) -> Unit,
    val onCancelled: (
        requestId: Long,
        reason: ImageGenerationCancellationReason,
        phase: String,
    ) -> Unit,
    val onFinished: () -> Unit,
)

internal interface ImageGenerationRuntime {
    suspend fun generate(request: ImageGenerationRequest): Bitmap

    fun cancelGeneration()

    fun getLastGenerationMetrics(): GenerationMetrics?
}

internal class EdgeImageGenerationRuntime(
    private val edge: LLMEdge,
) : ImageGenerationRuntime {
    override suspend fun generate(request: ImageGenerationRequest): Bitmap = edge.image.generate(request)

    override fun cancelGeneration() {
        edge.image.cancelGeneration()
    }

    override fun getLastGenerationMetrics(): GenerationMetrics? = edge.image.getLastGenerationMetrics()
}

internal class ImageGenerationController(
    private val scope: CoroutineScope,
    private val runtime: ImageGenerationRuntime,
    private val tag: String = "ImageGenerationActivity",
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    private val requestIds = AtomicLong(0L)

    private var generationJob: Job? = null
    private var activeRequestId: Long? = null
    private var currentPhaseText: String = "idle"
    private var cancellationReason: ImageGenerationCancellationReason? = null

    fun isGenerating(): Boolean = generationJob?.isActive == true

    fun currentRequestId(): Long? = activeRequestId

    fun currentPhaseText(): String = currentPhaseText

    fun start(
        config: ImageGenerationConfig,
        callbacks: ImageGenerationCallbacks,
    ) {
        check(!isGenerating()) { "Image generation already running" }

        val requestId = requestIds.incrementAndGet()
        activeRequestId = requestId
        cancellationReason = null
        updatePhase("preparing request")

        logInfo(
            "Image request started: requestId=$requestId, width=${config.request.width}, " +
                "height=${config.request.height}, steps=${config.request.steps}, " +
                "flash=${config.request.flashAttention}, loraRequested=${config.loraRequested}",
        )

        generationJob =
            scope.launch(ioDispatcher) {
                try {
                    withContext(mainDispatcher) {
                        callbacks.onProgress(0, "Preparing...")
                    }

                    updatePhase("generating image")
                    withContext(mainDispatcher) {
                        callbacks.onProgress(0, "Generating image...")
                    }

                    val bitmap = runtime.generate(config.request)
                    val metrics = runtime.getLastGenerationMetrics()
                    logInfo(
                        "Image request completed: requestId=$requestId, phase=$currentPhaseText, metricsAvailable=${metrics != null}",
                    )

                    withContext(mainDispatcher) {
                        callbacks.onCompleted(
                            ImageGenerationResult(
                                requestId = requestId,
                                bitmap = bitmap,
                                metrics = metrics,
                            ),
                        )
                    }
                } catch (_: CancellationException) {
                    val reason = cancellationReason ?: ImageGenerationCancellationReason.USER_CANCEL
                    val phase = currentPhaseText
                    logInfo(
                        "Image request cancelled: requestId=$requestId, phase=$phase, reason=${reason.logLabel}",
                    )
                    withContext(NonCancellable + mainDispatcher) {
                        callbacks.onCancelled(requestId, reason, phase)
                    }
                } catch (oom: OutOfMemoryError) {
                    cancellationReason = ImageGenerationCancellationReason.ERROR
                    updatePhase("out of memory")
                    logError(
                        "Image request failed with OOM: requestId=$requestId, phase=$currentPhaseText",
                        oom,
                    )
                    withContext(NonCancellable + mainDispatcher) {
                        callbacks.onProgress(0, "Out of memory. Close other apps and try again.")
                    }
                } catch (t: Throwable) {
                    cancellationReason = ImageGenerationCancellationReason.ERROR
                    updatePhase("failed")
                    logError(
                        "Image request failed: requestId=$requestId, phase=$currentPhaseText, message=${t.message}",
                        t,
                    )
                    withContext(NonCancellable + mainDispatcher) {
                        callbacks.onProgress(0, "Failed: ${t.localizedMessage}")
                    }
                } finally {
                    activeRequestId = null
                    updatePhase("idle")
                    cancellationReason = null
                    withContext(NonCancellable + mainDispatcher) {
                        generationJob = null
                        callbacks.onFinished()
                    }
                }
            }
        generationJob?.invokeOnCompletion { cause ->
            val requestId = activeRequestId ?: requestId
            val causeLabel =
                when (cause) {
                    null -> "completed"
                    is CancellationException -> "cancelled:${cause.message}"
                    else -> "failed:${cause.message}"
                }
            logInfo(
                "Image request job completion observed: requestId=$requestId, phase=$currentPhaseText, result=$causeLabel",
            )
        }
    }

    fun cancel(reason: ImageGenerationCancellationReason) {
        if (!isGenerating()) {
            return
        }
        cancellationReason = reason
        updatePhase("cancelling (${reason.statusLabel})")
        logInfo(
            "Image request cancellation requested: requestId=${activeRequestId ?: -1L}, reason=${reason.logLabel}, phase=$currentPhaseText",
        )
        generationJob?.cancel(CancellationException("Image generation cancelled: ${reason.logLabel}"))
        runtime.cancelGeneration()
    }

    private fun updatePhase(phase: String) {
        currentPhaseText = phase
    }

    private fun logInfo(message: String) {
        // FileLogger.i writes to the shared log file AND logcat; guard so a mocked/absent
        // android.util.Log (unit tests) still falls back to stdout.
        try {
            FileLogger.i(tag, message)
        } catch (_: Throwable) {
            println("I/$tag: $message")
        }
    }

    private fun logError(
        message: String,
        throwable: Throwable,
    ) {
        try {
            FileLogger.e(tag, message, throwable)
        } catch (_: Throwable) {
            System.err.println("E/$tag: $message")
            System.err.println(throwable.stackTraceToString())
        }
    }
}
