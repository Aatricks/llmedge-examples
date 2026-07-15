package com.example.llmedgeexample.demo.image

import android.graphics.Bitmap
import io.aatricks.llmedge.image.ImageGenerationRequest
import io.aatricks.llmedge.image.GenerationStreamEvent
import io.aatricks.llmedge.image.diffusion.GenerationMetrics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
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

    @Test
    fun `generateStream progress updates percents and completes with bitmap`() = runBlocking {
        val runtime = FakeImageGenerationRuntime().apply {
            emitProgressInTest = true
        }
        val controller = newController(runtime, this)
        val callbacks = RecordingCallbacks()

        controller.start(
            config = ImageGenerationConfig(
                request = ImageGenerationRequest(prompt = "test progress", width = 128, height = 128, steps = 4),
            ),
            callbacks = callbacks.asCallbacks(),
        )

        waitUntil { runtime.enteredGenerate }
        val mockBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        runtime.complete(mockBitmap)
        waitUntil { callbacks.finishedCount == 1 }

        val activePercents = callbacks.progressPercents.filter { it >= 1 }
        assertTrue("Expected at least one progress percent >= 1", activePercents.isNotEmpty())
        for (i in 0 until activePercents.size - 1) {
            assertTrue("Expected strictly increasing percents, got $activePercents", activePercents[i] < activePercents[i + 1])
        }

        assertNotNull(callbacks.completed)
        assertEquals(mockBitmap, callbacks.completed?.bitmap)
    }

    @Test
    fun `startUpscale drives progress and completes with 4x bitmap`() = runBlocking {
        val runtime = FakeImageGenerationRuntime()
        val controller = newController(runtime, this)
        val callbacks = RecordingCallbacks()
        val inputBitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

        val fakeDownloader = UpscalerAssetDownloader { _, _ -> java.io.File.createTempFile("fake", ".safetensors") }
        // Detached scope: attaching LLMEdge's supervisor to runBlocking would keep the test alive forever.
        val fakeEdgeScope = CoroutineScope(Dispatchers.Default)
        val fakeEdge = io.aatricks.llmedge.LLMEdge.create(androidx.test.core.app.ApplicationProvider.getApplicationContext(), fakeEdgeScope)

        controller.startUpscale(
            edge = fakeEdge,
            bitmap = inputBitmap,
            downloader = fakeDownloader,
            callbacks = callbacks.asCallbacks()
        )

        waitUntil { callbacks.finishedCount == 1 }

        assertEquals(listOf("Downloading upscaler...", "Upscaling 4x...", "Upscale complete"), callbacks.progressMessages)
        assertNotNull(callbacks.upscaled)
        assertEquals(40, callbacks.upscaled!!.width)
        assertEquals(40, callbacks.upscaled!!.height)
    }

    @Test
    fun `startUpscale while generation in flight does nothing`() = runBlocking {
        val runtime = FakeImageGenerationRuntime()
        val controller = newController(runtime, this)
        val callbacks = RecordingCallbacks()

        controller.start(
            config = ImageGenerationConfig(request = ImageGenerationRequest(prompt = "test", width = 128, height = 128, steps = 20)),
            callbacks = callbacks.asCallbacks(),
        )

        waitUntil { runtime.enteredGenerate }

        // Detached scope: attaching LLMEdge's supervisor to runBlocking would keep the test alive forever.
        val fakeEdgeScope = CoroutineScope(Dispatchers.Default)
        val fakeEdge = io.aatricks.llmedge.LLMEdge.create(androidx.test.core.app.ApplicationProvider.getApplicationContext(), fakeEdgeScope)
        val fakeDownloader = UpscalerAssetDownloader { _, _ -> java.io.File.createTempFile("fake", ".safetensors") }
        val upscaleCallbacks = RecordingCallbacks()

        controller.startUpscale(
            edge = fakeEdge,
            bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888),
            downloader = fakeDownloader,
            callbacks = upscaleCallbacks.asCallbacks()
        )

        assertEquals(0, upscaleCallbacks.progressMessages.size)
        assertNull(upscaleCallbacks.upscaled)

        // Unblock the in-flight generation so runBlocking's children can complete.
        runtime.complete(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        waitUntil { callbacks.finishedCount == 1 }
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
        var emitProgressInTest: Boolean = false

        override suspend fun generate(request: ImageGenerationRequest): Bitmap {
            enteredGenerate = true
            return result.await()
        }

        override fun generateStream(request: ImageGenerationRequest): Flow<GenerationStreamEvent> =
            kotlinx.coroutines.flow.callbackFlow {
                enteredGenerate = true
                val job = launch {
                    try {
                        if (emitProgressInTest) {
                            trySend(GenerationStreamEvent.Progress(io.aatricks.llmedge.core.ProgressEvent.Step("Sampling", 1, 4)))
                            trySend(GenerationStreamEvent.Progress(io.aatricks.llmedge.core.ProgressEvent.Step("Sampling", 2, 4)))
                        }
                        val bitmap = result.await()
                        trySend(GenerationStreamEvent.Completed(listOf(bitmap)))
                        close()
                    } catch (e: Throwable) {
                        close(e)
                    }
                }
                awaitClose {
                    job.cancel()
                }
            }

        override fun cancelGeneration() {
            cancelCalls += 1
            result.cancel()
        }

        override fun getLastGenerationMetrics(): GenerationMetrics? = metrics

        override suspend fun upscale(request: io.aatricks.llmedge.image.UpscaleRequest): Bitmap {
            return Bitmap.createBitmap(request.input.width * 4, request.input.height * 4, Bitmap.Config.ARGB_8888)
        }

        fun complete(bitmap: Bitmap) {
            result.complete(bitmap)
        }
    }

    private class RecordingCallbacks {
        val progressMessages = mutableListOf<String>()
        val progressPercents = mutableListOf<Int>()
        var completed: ImageGenerationResult? = null
        var upscaled: Bitmap? = null
        var cancelledReason: ImageGenerationCancellationReason? = null
        var finishedCount: Int = 0

        fun asCallbacks(): ImageGenerationCallbacks =
            ImageGenerationCallbacks(
                onProgress = { percent, status ->
                    progressPercents += percent
                    progressMessages += status
                },
                onCompleted = { completed = it },
                onUpscaled = { upscaled = it },
                onCancelled = { _, reason, _ -> cancelledReason = reason },
                onFinished = { finishedCount += 1 },
            )
    }
}
