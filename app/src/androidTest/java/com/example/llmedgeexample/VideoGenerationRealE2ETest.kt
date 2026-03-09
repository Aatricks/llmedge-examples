package com.example.llmedgeexample

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.aatricks.llmedge.image.diffusion.StableDiffusion
import io.aatricks.llmedge.image.diffusion.SampleMethod
import io.aatricks.llmedge.image.diffusion.VideoGenerateParams
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Real end-to-end test for video generation that actually loads models and generates video.
 *
 * This test:
 * - Loads real Wan 2.1 T2V models from HuggingFace
 * - Monitors memory usage throughout the process
 * - Attempts actual video generation
 * - Reports detailed metrics and failure points
 *
 * Run via adb:
 * adb shell am instrument -w -e class com.example.llmedgeexample.VideoGenerationRealE2ETest \
 *   com.example.llmedgeexample.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class VideoGenerationRealE2ETest {

    private lateinit var context: Context
    private var sd: StableDiffusion? = null

    companion object {
        private const val TAG = "VideoE2E_Real"

        // Wan 2.1 T2V models - same as HeadlessVideoTestActivity
        private const val WAN_MODEL_ID = "Comfy-Org/Wan_2.1_ComfyUI_repackaged"
        private const val WAN_MODEL_FILENAME = "wan2.1_t2v_1.3B_fp16.safetensors"
        private const val WAN_VAE_ID = "Comfy-Org/Wan_2.1_ComfyUI_repackaged"
        private const val WAN_VAE_FILENAME = "wan_2.1_vae.safetensors"
        private const val WAN_T5XXL_ID = "city96/umt5-xxl-encoder-gguf"
        private const val WAN_T5XXL_FILENAME = "umt5-xxl-encoder-Q3_K_S.gguf"

        // Memory thresholds
        private const val BYTES_IN_MB = 1024L * 1024L
        private const val MEMORY_WARNING_THRESHOLD_MB = 500L // Warn if available memory < 500MB
    }

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        logMemoryState("Test setup")
    }

    @After
    fun teardown() {
        sd?.close()
        sd = null
        logMemoryState("Test teardown")

        // Force GC to clean up
        System.gc()
        Thread.sleep(100)
    }

    /**
     * Test the complete video generation workflow with memory monitoring
     */
    @Test
    fun testCompleteVideoGenerationWithMemoryMonitoring() = runBlocking {
        android.util.Log.e(TAG, "========================================")
        android.util.Log.e(TAG, "REAL E2E VIDEO GENERATION TEST")
        android.util.Log.e(TAG, "========================================")

        val testStartTime = System.currentTimeMillis()

        try {
            // Phase 1: Check initial memory
            android.util.Log.e(TAG, "Phase 1: Initial Memory Check")
            logMemoryState("Before model loading")
            checkMemoryAvailability(required = 6000) // Need at least 6GB for models

            // Phase 2: Load main model
            android.util.Log.e(TAG, "")
            android.util.Log.e(TAG, "Phase 2: Loading Main Model")
            val modelLoadStart = System.currentTimeMillis()

            val modelFile = io.aatricks.llmedge.huggingface.HuggingFaceHub.ensureRepoFileOnDisk(
                context = context,
                modelId = WAN_MODEL_ID,
                revision = "main",
                filename = WAN_MODEL_FILENAME,
                allowedExtensions = listOf(".safetensors", ".gguf"),
                token = null,
                forceDownload = false,
                preferSystemDownloader = true,
                onProgress = null
            )
            android.util.Log.e(TAG, "✓ Main model file: ${modelFile.file.absolutePath}")
            android.util.Log.e(TAG, "  Size: ${modelFile.file.length() / BYTES_IN_MB}MB")
            logMemoryState("After main model file loaded")

            // Phase 3: Load VAE
            android.util.Log.e(TAG, "")
            android.util.Log.e(TAG, "Phase 3: Loading VAE")
            val vaeFile = io.aatricks.llmedge.huggingface.HuggingFaceHub.ensureRepoFileOnDisk(
                context = context,
                modelId = WAN_VAE_ID,
                revision = "main",
                filename = WAN_VAE_FILENAME,
                allowedExtensions = listOf(".safetensors"),
                token = null,
                forceDownload = false,
                preferSystemDownloader = true,
                onProgress = null
            )
            android.util.Log.e(TAG, "✓ VAE file: ${vaeFile.file.absolutePath}")
            android.util.Log.e(TAG, "  Size: ${vaeFile.file.length() / BYTES_IN_MB}MB")
            logMemoryState("After VAE file loaded")

            // Phase 4: Load T5XXL encoder
            android.util.Log.e(TAG, "")
            android.util.Log.e(TAG, "Phase 4: Loading T5XXL Encoder")
            val t5xxlFile = io.aatricks.llmedge.huggingface.HuggingFaceHub.ensureRepoFileOnDisk(
                context = context,
                modelId = WAN_T5XXL_ID,
                revision = "main",
                filename = WAN_T5XXL_FILENAME,
                allowedExtensions = listOf(".gguf", ".safetensors"),
                token = null,
                forceDownload = false,
                preferSystemDownloader = true,
                onProgress = null
            )
            android.util.Log.e(TAG, "✓ T5XXL file: ${t5xxlFile.file.absolutePath}")
            android.util.Log.e(TAG, "  Size: ${t5xxlFile.file.length() / BYTES_IN_MB}MB")
            logMemoryState("After T5XXL file loaded")

            // Phase 5: Initialize StableDiffusion context
            android.util.Log.e(TAG, "")
            android.util.Log.e(TAG, "Phase 5: Initializing StableDiffusion Context")
            android.util.Log.e(TAG, "This is where crashes typically occur...")

            val initStart = System.currentTimeMillis()
            logMemoryState("Before StableDiffusion.load()")

            sd = StableDiffusion.load(
                context = context,
                modelPath = modelFile.file.absolutePath,
                vaePath = vaeFile.file.absolutePath,
                t5xxlPath = t5xxlFile.file.absolutePath,
                nThreads = io.aatricks.llmedge.runtime.CpuTopology.getOptimalThreadCount(io.aatricks.llmedge.runtime.CpuTopology.TaskType.DIFFUSION),
                offloadToCpu = true,
                keepClipOnCpu = true,
                keepVaeOnCpu = true,
            )

            val initDuration = System.currentTimeMillis() - initStart
            android.util.Log.e(TAG, "✓ StableDiffusion context initialized in ${initDuration}ms")
            logMemoryState("After StableDiffusion.load()")

            assertNotNull(sd, "StableDiffusion instance should not be null")

            // Phase 6: Verify video model
            android.util.Log.e(TAG, "")
            android.util.Log.e(TAG, "Phase 6: Model Verification")
            val isVideo = sd?.isVideoModel() ?: false
            android.util.Log.e(TAG, "Is video model: $isVideo")
            assertTrue(isVideo, "Model should be detected as video model")

            // Phase 7: Configure generation parameters
            android.util.Log.e(TAG, "")
            android.util.Log.e(TAG, "Phase 7: Preparing Generation Parameters")
            val params = VideoGenerateParams(
                prompt = "a cat walking in a garden, high quality",
                videoFrames = 8,
                width = 256,
                height = 256,
                steps = 10,
                cfgScale = 7.0f,
                seed = 42,
                sampleMethod = SampleMethod.EULER
            )

            logMemoryState("Before generation")

            // Phase 8: Generate video
            android.util.Log.e(TAG, "")
            android.util.Log.e(TAG, "Phase 8: Generating Video")
            val genStart = System.currentTimeMillis()

            var progressCount = 0
            val frames = sd?.txt2vid(params) { step, totalSteps, currentFrame, totalFrames, timePerStep ->
                progressCount++
                if (progressCount % 5 == 0) { // Log every 5th progress update
                    android.util.Log.e(TAG, "Progress: Step $step/$totalSteps, Frame $currentFrame/$totalFrames")
                    logMemoryState("During generation", verbose = false)
                }
            }

            val genDuration = System.currentTimeMillis() - genStart
            android.util.Log.e(TAG, "✓ Video generation completed in ${genDuration}ms")
            logMemoryState("After generation")

            // Phase 9: Validate results
            android.util.Log.e(TAG, "")
            android.util.Log.e(TAG, "Phase 9: Validating Results")
            assertNotNull(frames, "Generated frames should not be null")
            assertEquals(8, frames.size, "Should generate 8 frames")

            frames.forEachIndexed { index, frame ->
                assertNotNull(frame, "Frame $index should not be null")
                assertEquals(256, frame.width, "Frame $index width should be 256")
                assertEquals(256, frame.height, "Frame $index height should be 256")
            }
            android.util.Log.e(TAG, "✓ All frames validated")

            // Phase 10: Check metrics
            android.util.Log.e(TAG, "")
            android.util.Log.e(TAG, "Phase 10: Performance Metrics")
            val metrics = sd?.getLastGenerationMetrics()
            if (metrics != null) {
                android.util.Log.e(TAG, "Total time: ${String.format("%.2f", metrics.totalTimeSeconds)}s")
                android.util.Log.e(TAG, "Frames/sec: ${String.format("%.2f", metrics.framesPerSecond)}")
                android.util.Log.e(TAG, "Time/step: ${String.format("%.3f", metrics.timePerStep)}s")
                android.util.Log.e(TAG, "Peak memory: ${metrics.peakMemoryUsageMb}MB")
            }

            // Final summary
            val totalDuration = System.currentTimeMillis() - testStartTime
            android.util.Log.e(TAG, "")
            android.util.Log.e(TAG, "========================================")
            android.util.Log.e(TAG, "TEST PASSED ✓")
            android.util.Log.e(TAG, "========================================")
            android.util.Log.e(TAG, "Total test duration: ${totalDuration}ms")
            android.util.Log.e(TAG, "Model loading: ${initDuration}ms")
            android.util.Log.e(TAG, "Video generation: ${genDuration}ms")
            android.util.Log.e(TAG, "Progress updates: $progressCount")
            logMemoryState("Final state")
            android.util.Log.e(TAG, "========================================")

        } catch (e: OutOfMemoryError) {
            android.util.Log.e(TAG, "")
            android.util.Log.e(TAG, "========================================")
            android.util.Log.e(TAG, "TEST FAILED: OUT OF MEMORY")
            android.util.Log.e(TAG, "========================================")
            android.util.Log.e(TAG, "Error: ${e.message}")
            logMemoryState("At OOM failure")
            android.util.Log.e(TAG, "========================================")
            throw e

        } catch (e: Exception) {
            android.util.Log.e(TAG, "")
            android.util.Log.e(TAG, "========================================")
            android.util.Log.e(TAG, "TEST FAILED: EXCEPTION")
            android.util.Log.e(TAG, "========================================")
            android.util.Log.e(TAG, "Error: ${e.message}")
            android.util.Log.e(TAG, "Type: ${e.javaClass.simpleName}")
            e.printStackTrace()
            logMemoryState("At exception")
            android.util.Log.e(TAG, "========================================")
            throw e
        }
    }

    /**
     * Test with minimal parameters to reduce memory pressure
     */
    @Test
    fun testMinimalVideoGeneration() = runBlocking {
        android.util.Log.e(TAG, "========================================")
        android.util.Log.e(TAG, "MINIMAL VIDEO GENERATION TEST")
        android.util.Log.e(TAG, "========================================")

        try {
            logMemoryState("Initial")

            // Use smallest possible parameters
            val modelFile = io.aatricks.llmedge.huggingface.HuggingFaceHub.ensureRepoFileOnDisk(
                context = context,
                modelId = WAN_MODEL_ID,
                revision = "main",
                filename = WAN_MODEL_FILENAME,
                allowedExtensions = listOf(".safetensors", ".gguf"),
                token = null,
                forceDownload = false,
                preferSystemDownloader = true,
                onProgress = null
            )

            val vaeFile = io.aatricks.llmedge.huggingface.HuggingFaceHub.ensureRepoFileOnDisk(
                context = context,
                modelId = WAN_VAE_ID,
                revision = "main",
                filename = WAN_VAE_FILENAME,
                allowedExtensions = listOf(".safetensors"),
                token = null,
                forceDownload = false,
                preferSystemDownloader = true,
                onProgress = null
            )

            val t5xxlFile = io.aatricks.llmedge.huggingface.HuggingFaceHub.ensureRepoFileOnDisk(
                context = context,
                modelId = WAN_T5XXL_ID,
                revision = "main",
                filename = WAN_T5XXL_FILENAME,
                allowedExtensions = listOf(".gguf", ".safetensors"),
                token = null,
                forceDownload = false,
                preferSystemDownloader = true,
                onProgress = null
            )

            android.util.Log.e(TAG, "Loading with maximum memory optimizations...")
            logMemoryState("Before load")

            sd = StableDiffusion.load(
                context = context,
                modelPath = modelFile.file.absolutePath,
                vaePath = vaeFile.file.absolutePath,
                t5xxlPath = t5xxlFile.file.absolutePath,
                nThreads = 2, // Minimal threads
                offloadToCpu = true,
                keepClipOnCpu = true,
                keepVaeOnCpu = true,
            )

            android.util.Log.e(TAG, "✓ Model loaded")
            logMemoryState("After load")

            // Generate minimal video
            val params = VideoGenerateParams(
                prompt = "test",
                videoFrames = 4, // Minimum frames
                width = 256, // Minimum size
                height = 256,
                steps = 5, // Minimum steps
                cfgScale = 7.0f,
                seed = 42
            )

            android.util.Log.e(TAG, "Generating 4 frames at 256x256...")
            val frames = sd?.txt2vid(params)

            assertNotNull(frames)
            assertEquals(4, frames.size)
            android.util.Log.e(TAG, "✓ Minimal generation successful")
            logMemoryState("Final")

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Minimal test failed: ${e.message}", e)
            logMemoryState("At failure")
            throw e
        }
    }

    private fun logMemoryState(phase: String, verbose: Boolean = true) {
        val runtime = Runtime.getRuntime()
        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.getMemoryInfo(memoryInfo)

        val totalRamMB = memoryInfo.totalMem / BYTES_IN_MB
        val availRamMB = memoryInfo.availMem / BYTES_IN_MB
        val thresholdMB = memoryInfo.threshold / BYTES_IN_MB
        val usedRamMB = totalRamMB - availRamMB
        val usedPercent = (usedRamMB.toFloat() / totalRamMB.toFloat() * 100).toInt()

        val heapMax = runtime.maxMemory() / BYTES_IN_MB
        val heapTotal = runtime.totalMemory() / BYTES_IN_MB
        val heapUsed = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_IN_MB
        val heapFree = heapMax - heapUsed

        val nativeHeap = Debug.getNativeHeapAllocatedSize() / BYTES_IN_MB
        val nativeHeapFree = Debug.getNativeHeapFreeSize() / BYTES_IN_MB

        if (verbose) {
            android.util.Log.e(TAG, "Memory State: $phase")
            android.util.Log.e(TAG, "  System RAM: ${usedRamMB}MB used / ${totalRamMB}MB total ($usedPercent%)")
            android.util.Log.e(TAG, "  Available: ${availRamMB}MB (threshold: ${thresholdMB}MB)")
            android.util.Log.e(TAG, "  Heap: ${heapUsed}MB / ${heapMax}MB (${heapFree}MB free)")
            android.util.Log.e(TAG, "  Native heap: ${nativeHeap}MB allocated, ${nativeHeapFree}MB free")
            android.util.Log.e(TAG, "  Low memory: ${memoryInfo.lowMemory}")
        } else {
            android.util.Log.e(
                TAG,
                "[$phase] RAM: ${usedRamMB}/${totalRamMB}MB ($usedPercent%), Heap: ${heapUsed}/${heapMax}MB"
            )
        }

        // Warn if memory is critically low
        if (availRamMB < MEMORY_WARNING_THRESHOLD_MB) {
            android.util.Log.w(TAG, "⚠️  WARNING: Low memory! Only ${availRamMB}MB available")
        }

        if (memoryInfo.lowMemory) {
            android.util.Log.w(TAG, "⚠️  WARNING: System reports low memory condition")
        }
    }

    private fun checkMemoryAvailability(required: Long) {
        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.getMemoryInfo(memoryInfo)

        val availMB = memoryInfo.availMem / BYTES_IN_MB

        android.util.Log.e(TAG, "Memory check: ${availMB}MB available, ${required}MB required")

        if (availMB < required) {
            android.util.Log.w(TAG, "⚠️  WARNING: Insufficient memory for test")
            android.util.Log.w(TAG, "   Available: ${availMB}MB")
            android.util.Log.w(TAG, "   Required: ${required}MB")
            android.util.Log.w(TAG, "   Deficit: ${required - availMB}MB")
            android.util.Log.w(TAG, "   This test may fail with OOM")
        } else {
            android.util.Log.e(TAG, "✓ Sufficient memory available")
        }
    }
}
