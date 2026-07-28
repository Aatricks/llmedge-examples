package com.example.llmedgeexample.demo.video

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.example.llmedgeexample.common.ImportedModelSupport
import com.example.llmedgeexample.demo.image.ImageGenerationRequestPreparer
import com.example.llmedgeexample.demo.image.ImageLoraAssetDownloader
import com.example.llmedgeexample.demo.image.ImageModelPreset
import io.aatricks.llmedge.LLMEdge
import io.aatricks.llmedge.image.diffusion.StableDiffusion
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class Issue31WanDeviceE2ETest {
    @Test
    fun importedWanGgufLoadsInNativeRuntime() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(
            "Enable with -e llmedge.issue31WanE2E 1",
            InstrumentationRegistry.getArguments().getString("llmedge.issue31WanE2E") == "1",
        )
        assumeTrue("Requires arm64 device", Build.SUPPORTED_ABIS.any { it.contains("arm64") })

        val context = instrumentation.targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val edge = LLMEdge.create(context, scope)
        try {
            val preset = VideoGenerationFormSupport.modelPresets.first()
            val downloadedModel = edge.models.resolve(requireNotNull(preset.model))
            val downloadedVae = edge.models.resolve(requireNotNull(preset.vae))
            val imported =
                downloadedModel.inputStream().use { input ->
                    ImportedModelSupport.copyToAppStorage(
                        context = context,
                        displayName = "custom-finetune.gguf",
                        input = input,
                        internalNamePrefix = "wan-",
                    )
                }

            assertTrue(imported.file.name.startsWith("wan-"))
            withTimeout(30 * 60_000L) {
                StableDiffusion.load(
                    context = context,
                    diffusionModelPath = imported.file.absolutePath,
                    vaePath = downloadedVae.absolutePath,
                    offloadToCpu = true,
                    keepClipOnCpu = true,
                    keepVaeOnCpu = true,
                    sequentialLoad = true,
                    allowVulkan = true,
                    preferPerformanceMode = true,
                ).use { model ->
                    assertTrue(model.isEasyCacheSupported())
                }
            }
        } finally {
            edge.close()
            scope.cancel()
        }
    }

    @Test
    fun hyperSd3AndEasyCacheOptionsReachDeviceRequests() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val edge = LLMEdge.create(context, scope)
        val loraDirectory = File(context.filesDir, "hyper-sd3").apply { mkdirs() }
        val loraFile =
            File(loraDirectory, "Hyper-SD3-4steps-CFG-lora.safetensors").apply {
                writeText("device-test")
            }
        try {
            val baseRequest =
                ImageModelPreset.SD3_MEDIUM.buildRequest(
                    prompt = "city skyline",
                    width = 256,
                    height = 256,
                    steps = 28,
                    cfg = 4.5f,
                    seed = 42L,
                    flashAttention = true,
                    easyCacheEnabled = false,
                )
            val prepared =
                ImageGenerationRequestPreparer(
                    hyperSd3Downloader = ImageLoraAssetDownloader { _, _ -> loraFile },
                ).prepare(
                    edge = edge,
                    baseRequest = baseRequest,
                    loraRequested = false,
                    hyperSd3Requested = true,
                )

            assertTrue(prepared.loraApplied)
            assertEquals(4, prepared.request.steps)
            assertEquals(3.0f, prepared.request.cfgScale)
            assertTrue(prepared.request.prompt.endsWith("<lora:Hyper-SD3-4steps-CFG-lora:0.125>"))
            assertFalse(prepared.request.easyCache.enabled)
        } finally {
            edge.close()
            scope.cancel()
        }
    }

    @Test
    fun officialHyperSd3LoraDownloadsOnDevice() = runBlocking {
        assumeTrue(
            "Enable with -e llmedge.issue31HyperSd3E2E 1",
            InstrumentationRegistry.getArguments().getString("llmedge.issue31HyperSd3E2E") == "1",
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val edge = LLMEdge.create(context, scope)
        try {
            val prepared =
                withTimeout(15 * 60_000L) {
                    ImageGenerationRequestPreparer().prepare(
                        edge = edge,
                        baseRequest =
                            ImageModelPreset.SD3_MEDIUM.buildRequest(
                                prompt = "city skyline",
                                width = 256,
                                height = 256,
                                steps = 28,
                                cfg = 4.5f,
                                seed = 42L,
                                flashAttention = true,
                                easyCacheEnabled = false,
                            ),
                        loraRequested = false,
                        hyperSd3Requested = true,
                    )
                }

            val loraDirectory = requireNotNull(prepared.request.loraModelDir)
            val loraFile = File(loraDirectory, "Hyper-SD3-4steps-CFG-lora.safetensors")
            assertTrue(loraFile.isFile)
            assertTrue(loraFile.length() > 400L * 1024L * 1024L)
            assertEquals(4, prepared.request.steps)
            assertEquals(3.0f, prepared.request.cfgScale)
            assertFalse(prepared.request.easyCache.enabled)
        } finally {
            edge.close()
            scope.cancel()
        }
    }

    @Test
    fun hyperSd3LoraGeneratesImageOnDevice() = runBlocking {
        assumeTrue(
            "Enable with -e llmedge.issue31HyperSd3GenerationE2E 1",
            InstrumentationRegistry.getArguments()
                .getString("llmedge.issue31HyperSd3GenerationE2E") == "1",
        )
        assumeTrue("Requires arm64 device", Build.SUPPORTED_ABIS.any { it.contains("arm64") })

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val edge = LLMEdge.create(context, scope)
        try {
            val prepared =
                withTimeout(30 * 60_000L) {
                    ImageGenerationRequestPreparer().prepare(
                        edge = edge,
                        baseRequest =
                            ImageModelPreset.SD3_MEDIUM.buildRequest(
                                prompt = "a red fox in snow, detailed",
                                width = 256,
                                height = 256,
                                steps = 28,
                                cfg = 4.5f,
                                seed = 42L,
                                flashAttention = true,
                                easyCacheEnabled = false,
                            ),
                        loraRequested = false,
                        hyperSd3Requested = true,
                    )
                }

            assertEquals(true, prepared.request.sequential)
            val bitmap =
                withTimeout(90 * 60_000L) {
                    edge.image.generate(prepared.request)
                }

            assertEquals(256, bitmap.width)
            assertEquals(256, bitmap.height)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue(pixels.any { (it ushr 24) != 0 && (it and 0x00FFFFFF) != 0 })
            assertTrue(pixels.toSet().size > 1)

            val outputDirectory = requireNotNull(context.getExternalFilesDir("issue31"))
            val outputFile = File(outputDirectory, "hyper-sd3-4step-seed42.png")
            FileOutputStream(outputFile).use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            assertTrue(outputFile.isFile)
            assertTrue(outputFile.length() > 0L)
        } finally {
            edge.close()
            scope.cancel()
        }
    }

    /**
     * The configuration from llmedge-examples#37: the Hyper-SD3 sequential path at a non-square,
     * non-default resolution. Square 256x256 is already covered above; aspect ratio is the axis
     * that has previously broken diffusion runtimes here (see the MiniT2I positional-embedding
     * fix), and 576x320 is what the field report actually ran.
     */
    @Test
    fun hyperSd3LoraGeneratesNonSquareImageOnDevice() = runBlocking {
        assumeTrue(
            "Enable with -e llmedge.issue37NonSquareE2E 1",
            InstrumentationRegistry.getArguments()
                .getString("llmedge.issue37NonSquareE2E") == "1",
        )
        assumeTrue("Requires arm64 device", Build.SUPPORTED_ABIS.any { it.contains("arm64") })

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val edge = LLMEdge.create(context, scope)
        try {
            val prepared =
                withTimeout(30 * 60_000L) {
                    ImageGenerationRequestPreparer().prepare(
                        edge = edge,
                        baseRequest =
                            ImageModelPreset.SD3_MEDIUM.buildRequest(
                                prompt = "a red fox in snow, detailed",
                                width = 576,
                                height = 320,
                                steps = 28,
                                cfg = 4.5f,
                                seed = 42L,
                                flashAttention = false,
                                easyCacheEnabled = false,
                            ),
                        loraRequested = false,
                        hyperSd3Requested = true,
                    )
                }
            assertEquals(true, prepared.request.sequential)
            assertEquals(4, prepared.request.steps)

            val bitmap =
                withTimeout(90 * 60_000L) {
                    edge.image.generate(prepared.request)
                }

            assertEquals(576, bitmap.width)
            assertEquals(320, bitmap.height)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue("image is fully transparent or black", pixels.any { (it ushr 24) != 0 && (it and 0x00FFFFFF) != 0 })
            assertTrue("image is a single flat colour", pixels.toSet().size > 1)

            val outputDirectory = requireNotNull(context.getExternalFilesDir("issue31"))
            val outputFile = File(outputDirectory, "hyper-sd3-4step-576x320-seed42.png")
            FileOutputStream(outputFile).use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            assertTrue(outputFile.length() > 0L)
        } finally {
            edge.close()
            scope.cancel()
        }
    }
}
