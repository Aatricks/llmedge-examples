package com.example.llmedgeexample.demo.image

import io.aatricks.llmedge.image.MiniT2I
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageGenerationFormSupportTest {
    @Test
    fun `MiniT2I selection creates standalone diffusion request`() {
        val request =
            ImageGenerationFormSupport.createRequest(
                model = ImageGenerationModel.MINI_T2I,
                prompt = "a small robot",
                width = 512,
                height = 512,
                steps = 100,
                cfgScale = 6.0f,
                seed = 42L,
                flashAttention = true,
            )

        assertEquals(MiniT2I.diffusionModel, request.model)
        assertEquals(MiniT2I.textEncoder, request.textEncoder)
        assertTrue(request.diffusionModelOnly)
        assertEquals(100, request.steps)
        assertEquals(6.0f, request.cfgScale)
    }
}
