package com.example.llmedgeexample.demo.video

import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import io.aatricks.llmedge.model.ModelSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoGenerationFormSupportTest {
    @Test
    fun `one frame is accepted for image-style Wan output`() {
        val field = EditText(ApplicationProvider.getApplicationContext())
        field.setText("1")

        assertEquals(1, VideoGenerationFormSupport.parseFramesField(field, 9))
    }

    @Test
    fun `zero frames is rejected`() {
        val field = EditText(ApplicationProvider.getApplicationContext())
        field.setText("0")

        assertNull(VideoGenerationFormSupport.parseFramesField(field, 9))
        assertEquals("Frames must be between 1 and 64", field.error.toString())
    }

    @Test
    fun `model presets default to Wan 2_1 and include Wan 2_2 Q6`() {
        val presets = VideoGenerationFormSupport.modelPresets

        assertEquals("Wan 2.1 T2V 1.3B (default)", presets.first().displayName)
        assertNull(presets.first().model)
        assertNull(presets.first().vae)

        val wan22 = presets[1]
        assertEquals("Wan 2.2 TI2V 5B Q6_K", wan22.displayName)
        val model = wan22.model as? ModelSpec.HuggingFace
        val vae = wan22.vae as? ModelSpec.HuggingFace
        assertNotNull(model)
        assertNotNull(vae)
        assertEquals("QuantStack/Wan2.2-TI2V-5B-GGUF", model?.repoId)
        assertEquals("Wan2.2-TI2V-5B-Q6_K.gguf", model?.filename)
        assertEquals("QuantStack/Wan2.2-TI2V-5B-GGUF", vae?.repoId)
        assertEquals("VAE/Wan2.2_VAE.safetensors", vae?.filename)
        assertTrue(VideoGenerationFormSupport.selectedModelPreset(99) === presets.first())
    }
}
