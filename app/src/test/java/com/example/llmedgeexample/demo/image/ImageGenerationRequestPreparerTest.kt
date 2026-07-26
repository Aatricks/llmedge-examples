package com.example.llmedgeexample.demo.image

import androidx.test.core.app.ApplicationProvider
import io.aatricks.llmedge.LLMEdge
import io.aatricks.llmedge.image.ImageGenerationRequest
import io.aatricks.llmedge.image.diffusion.LoraApplyMode
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImageGenerationRequestPreparerTest {
    @Test
    fun `toggle off returns original request`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val edge = LLMEdge.create(context, this)
        val baseRequest = ImageGenerationRequest(prompt = "city skyline", width = 128, height = 128)
        val preparer =
            ImageGenerationRequestPreparer(
                loraDownloader = ImageLoraAssetDownloader { _, _ -> error("downloader should not be called") },
            )

        val prepared = try {
            preparer.prepare(edge, baseRequest, loraRequested = false)
        } finally {
            edge.close()
        }

        assertFalse(prepared.loraApplied)
        assertEquals(baseRequest, prepared.request)
        assertNull(prepared.warningMessage)
    }

    @Test
    fun `successful download appends lora tag and directory`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val edge = LLMEdge.create(context, this)
        val loraDirectory = File(context.filesDir, "loras").apply { mkdirs() }
        val loraFile = File(loraDirectory, "detail-tweaker.safetensors").apply { writeText("stub") }
        val statusMessages = mutableListOf<String>()
        val preparer =
            ImageGenerationRequestPreparer(
                loraDownloader = ImageLoraAssetDownloader { _, onProgress ->
                    onProgress(50L, 100L)
                    onProgress(100L, 100L)
                    loraFile
                },
            )

        val prepared =
            try {
                preparer.prepare(
                    edge = edge,
                    baseRequest = ImageGenerationRequest(prompt = "city skyline", width = 128, height = 128),
                    loraRequested = true,
                    onStatus = statusMessages::add,
                )
            } finally {
                edge.close()
            }

        assertTrue(prepared.loraApplied)
        assertEquals("city skyline <lora:detail-tweaker:1.0>", prepared.request.prompt)
        assertEquals(loraDirectory.absolutePath, prepared.request.loraModelDir)
        assertEquals(LoraApplyMode.AUTO, prepared.request.loraApplyMode)
        assertNull(prepared.warningMessage)
        assertTrue(statusMessages.any { it.contains("50%") })
    }

    @Test
    fun `download failure reports warning and continues without lora`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val edge = LLMEdge.create(context, this)
        val baseRequest = ImageGenerationRequest(prompt = "city skyline", width = 128, height = 128)
        val preparer =
            ImageGenerationRequestPreparer(
                loraDownloader = ImageLoraAssetDownloader { _, _ -> throw IllegalStateException("network down") },
            )

        val prepared = try {
            preparer.prepare(edge, baseRequest, loraRequested = true)
        } finally {
            edge.close()
        }

        assertFalse(prepared.loraApplied)
        assertEquals(baseRequest, prepared.request)
        assertEquals(
            "Failed to download Detail Tweaker LoRA: network down. Continuing without LoRA.",
            prepared.warningMessage,
        )
    }

    @Test
    fun `hyper sd3 option applies official four step settings and lora scale`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val edge = LLMEdge.create(context, this)
        val loraDirectory = File(context.filesDir, "hyper-sd3").apply { mkdirs() }
        val loraFile =
            File(loraDirectory, "Hyper-SD3-4steps-CFG-lora.safetensors").apply {
                writeText("stub")
            }
        val preparer =
            ImageGenerationRequestPreparer(
                loraDownloader = ImageLoraAssetDownloader { _, _ -> error("detail downloader should not be called") },
                hyperSd3Downloader = ImageLoraAssetDownloader { _, _ -> loraFile },
            )

        val prepared =
            try {
                preparer.prepare(
                    edge = edge,
                    baseRequest =
                        ImageGenerationRequest(
                            prompt = "city skyline",
                            width = 128,
                            height = 128,
                            steps = 28,
                            cfgScale = 4.5f,
                        ),
                    loraRequested = false,
                    hyperSd3Requested = true,
                )
            } finally {
                edge.close()
            }

        assertTrue(prepared.loraApplied)
        assertEquals(
            "city skyline <lora:Hyper-SD3-4steps-CFG-lora:0.125>",
            prepared.request.prompt,
        )
        assertEquals(4, prepared.request.steps)
        assertEquals(3.0f, prepared.request.cfgScale)
        assertEquals(loraDirectory.absolutePath, prepared.request.loraModelDir)
        assertEquals(true, prepared.request.sequential)
    }

    @Test(expected = CancellationException::class)
    fun `lora download cancellation is rethrown`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val edge = LLMEdge.create(context, this)
        val preparer =
            ImageGenerationRequestPreparer(
                loraDownloader =
                    ImageLoraAssetDownloader { _, _ ->
                        throw CancellationException("cancelled")
                    },
            )

        try {
            preparer.prepare(
                edge = edge,
                baseRequest = ImageGenerationRequest(prompt = "city skyline"),
                loraRequested = true,
            )
        } finally {
            edge.close()
        }
    }
}
