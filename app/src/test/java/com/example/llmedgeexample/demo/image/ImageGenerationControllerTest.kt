package com.example.llmedgeexample.demo.image

import android.graphics.Bitmap
import io.aatricks.llmedge.image.ImageGenerationRequest
import io.aatricks.llmedge.image.diffusion.GenerationMetrics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImageGenerationControllerTest {
    @Test
    fun `start request updates phase to generating`() = runBlocking {
        val runtime = FakeImageGenerationRuntime()
        val controller = newController(runtime, this)
        val callbacks = RecordingCallbacks()

        controller.start(
            config =
                ImageGenerationConfig(
                    request = ImageGenerationRequest(prompt = "test prompt", width = 128, height = 128, steps = 20),
                ),
            callbacks = callbacks.asCallbacks(),
        )

        waitUntil { runtime.enteredGenerate }
        assertTrue(controller.isGenerating())
        assertEquals("generating image", controller.currentPhaseText())
        assertNotNull(controller.currentRequestId())
        assertEquals(listOf("Preparing...", "Generating image..."), callbacks.progressMessages)

        runtime.complete(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        waitUntil { callbacks.finishedCount == 1 }

        assertFalse(controller.isGenerating())
        assertEquals("idle", controller.currentPhaseText())
        assertNotNull(callbacks.completed)
        assertEquals(1, callbacks.finishedCount)
    }

    @Test
    fun `screen leave while active cancels with SCREEN_LEFT`() = runBlocking {
        val runtime = FakeImageGenerationRuntime()
        val controller = newController(runtime, this)
        val callbacks = RecordingCallbacks()

        controller.start(
            config = ImageGenerationConfig(request = ImageGenerationRequest(prompt = "screen-left")),
            callbacks = callbacks.asCallbacks(),
        )

        waitUntil { runtime.enteredGenerate }
        controller.cancel(ImageGenerationCancellationReason.SCREEN_LEFT)
        waitUntil { callbacks.finishedCount == 1 }

        assertEquals(ImageGenerationCancellationReason.SCREEN_LEFT, callbacks.cancelledReason)
        assertNull(callbacks.completed)
        assertEquals(1, runtime.cancelCalls)
        assertEquals(1, callbacks.finishedCount)
        assertFalse(controller.isGenerating())
    }

    @Test
    fun `low memory while active cancels with LOW_MEMORY`() = runBlocking {
        val runtime = FakeImageGenerationRuntime()
        val controller = newController(runtime, this)
        val callbacks = RecordingCallbacks()

        controller.start(
            config = ImageGenerationConfig(request = ImageGenerationRequest(prompt = "low-memory")),
            callbacks = callbacks.asCallbacks(),
        )

        waitUntil { runtime.enteredGenerate }
        controller.cancel(ImageGenerationCancellationReason.LOW_MEMORY)
        waitUntil { callbacks.finishedCount == 1 }

        assertEquals(ImageGenerationCancellationReason.LOW_MEMORY, callbacks.cancelledReason)
        assertNull(callbacks.completed)
        assertEquals(1, runtime.cancelCalls)
        assertEquals(1, callbacks.finishedCount)
    }

    @Test
    fun `user cancel while active does not report success metrics`() = runBlocking {
        val runtime = FakeImageGenerationRuntime(
            metrics =
                GenerationMetrics(
                    totalTimeSeconds = 1.0f,
                    framesPerSecond = 1.0f,
                    timePerStep = 0.1f,
                    peakMemoryUsageMb = 1L,
                    vulkanEnabled = false,
                ),
        )
        val controller = newController(runtime, this)
        val callbacks = RecordingCallbacks()

        controller.start(
            config = ImageGenerationConfig(request = ImageGenerationRequest(prompt = "user-cancel")),
            callbacks = callbacks.asCallbacks(),
        )

        waitUntil { runtime.enteredGenerate }
        controller.cancel(ImageGenerationCancellationReason.USER_CANCEL)
        waitUntil { callbacks.finishedCount == 1 }

        assertEquals(ImageGenerationCancellationReason.USER_CANCEL, callbacks.cancelledReason)
        assertNull(callbacks.completed)
        assertEquals(1, runtime.cancelCalls)
        assertEquals(1, callbacks.finishedCount)
        assertEquals("idle", controller.currentPhaseText())
    }

    private fun newController(
        runtime: FakeImageGenerationRuntime,
        scope: CoroutineScope,
    ): ImageGenerationController =
        ImageGenerationController(
            scope = scope,
            runtime = runtime,
            tag = "ImageGenerationControllerTest",
            ioDispatcher = Dispatchers.Default,
            mainDispatcher = Dispatchers.Default,
        )

    private fun waitUntil(predicate: () -> Boolean) {
        repeat(100) {
            if (predicate()) {
                return
            }
            Thread.sleep(10)
        }
        throw AssertionError("Condition was not met in time")
    }

    private class FakeImageGenerationRuntime(
        private val metrics: GenerationMetrics? = null,
    ) : ImageGenerationRuntime {
        private val result = CompletableDeferred<Bitmap>()
        var enteredGenerate: Boolean = false
            private set
        var cancelCalls: Int = 0
            private set

        override suspend fun generate(request: ImageGenerationRequest): Bitmap {
            enteredGenerate = true
            return result.await()
        }

        override fun cancelGeneration() {
            cancelCalls += 1
            result.cancel()
        }

        override fun getLastGenerationMetrics(): GenerationMetrics? = metrics

        fun complete(bitmap: Bitmap) {
            result.complete(bitmap)
        }
    }

    private class RecordingCallbacks {
        val progressMessages = mutableListOf<String>()
        var completed: ImageGenerationResult? = null
        var cancelledReason: ImageGenerationCancellationReason? = null
        var finishedCount: Int = 0

        fun asCallbacks(): ImageGenerationCallbacks =
            ImageGenerationCallbacks(
                onProgress = { _, status -> progressMessages += status },
                onCompleted = { completed = it },
                onCancelled = { _, reason, _ -> cancelledReason = reason },
                onFinished = { finishedCount += 1 },
            )
    }
}
