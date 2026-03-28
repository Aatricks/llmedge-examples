package com.example.llmedgeexample

import androidx.test.core.app.ApplicationProvider
import io.aatricks.llmedge.image.ImageGenerationRequest
import io.aatricks.llmedge.image.diffusion.LoraApplyMode
import java.io.File
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
        val baseRequest = ImageGenerationRequest(prompt = "city skyline", width = 128, height = 128)
        val preparer =
            ImageGenerationRequestPreparer(
                loraDownloader = ImageLoraAssetDownloader { _, _ -> error("downloader should not be called") },
            )

        val prepared = preparer.prepare(context, baseRequest, loraRequested = false)

        assertFalse(prepared.loraApplied)
        assertEquals(baseRequest, prepared.request)
        assertNull(prepared.warningMessage)
    }

    @Test
    fun `successful download appends lora tag and directory`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
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
            preparer.prepare(
                context = context,
                baseRequest = ImageGenerationRequest(prompt = "city skyline", width = 128, height = 128),
                loraRequested = true,
                onStatus = statusMessages::add,
            )

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
        val baseRequest = ImageGenerationRequest(prompt = "city skyline", width = 128, height = 128)
        val preparer =
            ImageGenerationRequestPreparer(
                loraDownloader = ImageLoraAssetDownloader { _, _ -> throw IllegalStateException("network down") },
            )

        val prepared = preparer.prepare(context, baseRequest, loraRequested = true)

        assertFalse(prepared.loraApplied)
        assertEquals(baseRequest, prepared.request)
        assertEquals(
            "Failed to download Detail Tweaker LoRA: network down. Continuing without LoRA.",
            prepared.warningMessage,
        )
    }
}
