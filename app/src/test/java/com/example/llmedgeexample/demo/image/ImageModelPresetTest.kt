package com.example.llmedgeexample.demo.image

import io.aatricks.llmedge.image.Flux2Klein
import io.aatricks.llmedge.image.MiniT2I
import io.aatricks.llmedge.image.Sd3Medium
import io.aatricks.llmedge.model.ModelSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageModelPresetTest {

    @Test
    fun testPresetDefaults() {
        assertEquals(20, ImageModelPreset.SD15.defaultSteps)
        assertEquals(7.0f, ImageModelPreset.SD15.defaultCfg)
        assertTrue(ImageModelPreset.SD15.supportsLora)

        assertEquals(4, ImageModelPreset.FLUX2_KLEIN_BONSAI.defaultSteps)
        assertEquals(1.0f, ImageModelPreset.FLUX2_KLEIN_BONSAI.defaultCfg)
        assertFalse(ImageModelPreset.FLUX2_KLEIN_BONSAI.supportsLora)

        assertEquals(28, ImageModelPreset.SD3_MEDIUM.defaultSteps)
        assertEquals(4.5f, ImageModelPreset.SD3_MEDIUM.defaultCfg)
        assertFalse(ImageModelPreset.SD3_MEDIUM.supportsLora)

        assertEquals(100, ImageModelPreset.MINI_T2I.defaultSteps)
        assertEquals(6.0f, ImageModelPreset.MINI_T2I.defaultCfg)
        assertFalse(ImageModelPreset.MINI_T2I.supportsLora)

        assertEquals(100, ImageModelPreset.MINI_T2I_LARGE.defaultSteps)
        assertEquals(6.0f, ImageModelPreset.MINI_T2I_LARGE.defaultCfg)
        assertFalse(ImageModelPreset.MINI_T2I_LARGE.supportsLora)

        assertEquals(20, ImageModelPreset.CHROMA_RADIANCE.defaultSteps)
        assertEquals(4.0f, ImageModelPreset.CHROMA_RADIANCE.defaultCfg)
        assertFalse(ImageModelPreset.CHROMA_RADIANCE.supportsLora)

        assertEquals(20, ImageModelPreset.CHROMA_MOBILE.defaultSteps)
        assertEquals(4.0f, ImageModelPreset.CHROMA_MOBILE.defaultCfg)
        assertFalse(ImageModelPreset.CHROMA_MOBILE.supportsLora)

        ImageModelPreset.values().forEach { preset ->
            assertEquals(512, preset.defaultWidth)
            assertEquals(512, preset.defaultHeight)
        }
    }

    @Test
    fun testBuildRequestSd15() {
        val request = ImageModelPreset.SD15.buildRequest(
            prompt = "a small robot",
            width = 512,
            height = 512,
            steps = 20,
            cfg = 7.0f,
            seed = 42L,
            flashAttention = true,
        )

        assertNull(request.model)
        assertNull(request.textEncoder)
        assertNull(request.clipL)
        assertNull(request.clipG)
        assertFalse(request.splitDiffusionModel)
        assertNull(request.sequential)
        assertEquals(20, request.steps)
        assertEquals(7.0f, request.cfgScale)
    }

    @Test
    fun testBuildRequestFlux2Bonsai() {
        val request = ImageModelPreset.FLUX2_KLEIN_BONSAI.buildRequest(
            prompt = "a small robot",
            width = 512,
            height = 512,
            steps = 4,
            cfg = 1.0f,
            seed = 42L,
            flashAttention = true,
        )

        assertEquals(Flux2Klein.bonsaiDiffusionModel, request.model)
        assertEquals(Flux2Klein.vae, request.vae)
        assertEquals(Flux2Klein.textEncoder, request.textEncoder)
        assertTrue(request.splitDiffusionModel)
        assertEquals(true, request.sequential)
        assertEquals(4, request.steps)
    }

    @Test
    fun testBuildRequestSd3Medium() {
        val request = ImageModelPreset.SD3_MEDIUM.buildRequest(
            prompt = "a small robot",
            width = 512,
            height = 512,
            steps = 28,
            cfg = 4.5f,
            seed = 42L,
            flashAttention = true,
        )

        assertEquals(Sd3Medium.diffusionModel, request.model)
        assertEquals(Sd3Medium.vae, request.vae)
        assertEquals(Sd3Medium.clipL, request.clipL)
        assertEquals(Sd3Medium.clipG, request.clipG)
        assertEquals(Sd3Medium.t5xxl, request.t5xxl)
        assertNull(request.textEncoder)
        assertTrue(request.splitDiffusionModel)
        assertNull(request.sequential)
        assertEquals(28, request.steps)
        assertEquals(4.5f, request.cfgScale)
    }

    @Test
    fun testBuildRequestMiniT2i() {
        val request = ImageModelPreset.MINI_T2I.buildRequest(
            prompt = "a small robot",
            width = 512,
            height = 512,
            steps = 100,
            cfg = 6.0f,
            seed = 42L,
            flashAttention = true,
        )

        assertEquals(MiniT2I.diffusionModel, request.model)
        assertEquals(MiniT2I.textEncoder, request.textEncoder)
        assertTrue(request.diffusionModelOnly)
        assertEquals(100, request.steps)
        assertEquals(6.0f, request.cfgScale)
        assertNull(request.sequential)
    }

    @Test
    fun testBuildRequestMiniT2iLarge() {
        val request = ImageModelPreset.MINI_T2I_LARGE.buildRequest(
            prompt = "a small robot",
            width = 512,
            height = 512,
            steps = 100,
            cfg = 6.0f,
            seed = 42L,
            flashAttention = true,
        )

        assertEquals(MiniT2I.diffusionModelLarge, request.model)
        assertEquals(MiniT2I.textEncoder, request.textEncoder)
        assertTrue(request.diffusionModelOnly)
        assertEquals(100, request.steps)
        assertEquals(6.0f, request.cfgScale)
        assertNull(request.sequential)
    }

    @Test
    fun testBuildRequestChromaRadiance() {
        val request = ImageModelPreset.CHROMA_RADIANCE.buildRequest(
            prompt = "a small robot",
            width = 512,
            height = 512,
            steps = 20,
            cfg = 4.0f,
            seed = 42L,
            flashAttention = true,
        )

        assertEquals(io.aatricks.llmedge.image.ChromaRadiance.diffusionModel, request.model)
        assertEquals(io.aatricks.llmedge.image.ChromaRadiance.t5xxl, request.t5xxl)
        assertNull(request.vae)
        assertNull(request.clipL)
        assertNull(request.clipG)
        assertNull(request.textEncoder)
        assertTrue(request.splitDiffusionModel)
        assertEquals(true, request.sequential)
        assertEquals(20, request.steps)
        assertEquals(4.0f, request.cfgScale)
    }

    @Test
    fun testBuildRequestChromaMobile() {
        val request = ImageModelPreset.CHROMA_MOBILE.buildRequest(
            prompt = "a small robot",
            width = 512,
            height = 512,
            steps = 20,
            cfg = 4.0f,
            seed = 42L,
            flashAttention = true,
        )

        assertEquals(io.aatricks.llmedge.image.ChromaRadiance.mobileDiffusionModel, request.model)
        assertEquals(io.aatricks.llmedge.image.ChromaRadiance.t5xxl, request.t5xxl)
        assertEquals(io.aatricks.llmedge.image.ChromaRadiance.fluxVae, request.vae)
        assertTrue(request.splitDiffusionModel)
        assertEquals(true, request.sequential)
    }

    @Test
    fun testBuildRequestPassesNegativePrompt() {
        ImageModelPreset.values().forEach { preset ->
            val request = preset.buildRequest(
                prompt = "a small robot",
                negative = "blurry, low quality",
                width = 512,
                height = 512,
                steps = preset.defaultSteps,
                cfg = preset.defaultCfg,
                seed = 42L,
                flashAttention = true,
            )
            assertEquals("blurry, low quality", request.negative)
        }
    }

    @Test
    fun testBuildRequestDefaultsToEmptyNegativePrompt() {
        val request = ImageModelPreset.SD15.buildRequest(
            prompt = "a small robot",
            width = 512,
            height = 512,
            steps = 20,
            cfg = 7.0f,
            seed = 42L,
            flashAttention = true,
        )
        assertEquals("", request.negative)
    }

    @Test
    fun `local gguf overrides only the selected preset model and easy cache can be disabled`() {
        val localModel = ModelSpec.localFile("/models/sd3-finetune.gguf")

        val request =
            ImageModelPreset.SD3_MEDIUM.buildRequest(
                prompt = "a small robot",
                width = 512,
                height = 512,
                steps = 28,
                cfg = 4.5f,
                seed = 42L,
                flashAttention = true,
                modelOverride = localModel,
                easyCacheEnabled = false,
            )

        assertEquals(localModel, request.model)
        assertEquals(Sd3Medium.vae, request.vae)
        assertEquals(Sd3Medium.clipL, request.clipL)
        assertEquals(Sd3Medium.clipG, request.clipG)
        assertEquals(Sd3Medium.t5xxl, request.t5xxl)
        assertFalse(request.easyCache.enabled)
    }
}
