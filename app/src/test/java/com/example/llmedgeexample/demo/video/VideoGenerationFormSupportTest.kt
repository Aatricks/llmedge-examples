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
    fun `model presets default to mobile Wan 2_1 and retain fp16 and Wan 2_2`() {
        val presets = VideoGenerationFormSupport.modelPresets

        assertEquals("Wan 2.1 T2V 1.3B Q3_K_S (mobile default)", presets.first().displayName)
        val mobileModel = presets.first().model as? ModelSpec.HuggingFace
        val mobileVae = presets.first().vae as? ModelSpec.HuggingFace
        val mobileEncoder = presets.first().textEncoder as? ModelSpec.HuggingFace
        assertEquals("samuelchristlie/Wan2.1-T2V-1.3B-GGUF", mobileModel?.repoId)
        assertEquals("Wan2.1-T2V-1.3B-Q3_K_S.gguf", mobileModel?.filename)
        assertEquals("Comfy-Org/Wan_2.1_ComfyUI_repackaged", mobileVae?.repoId)
        assertEquals("wan_2.1_vae.safetensors", mobileVae?.filename)
        assertEquals("city96/umt5-xxl-encoder-gguf", mobileEncoder?.repoId)
        assertEquals("umt5-xxl-encoder-Q3_K_S.gguf", mobileEncoder?.filename)

        val fp16 = presets[1]
        assertEquals("Wan 2.1 T2V 1.3B fp16 (high memory)", fp16.displayName)
        assertEquals("wan2.1_t2v_1.3B_fp16.safetensors", (fp16.model as? ModelSpec.HuggingFace)?.filename)
        assertEquals("umt5-xxl-encoder-Q3_K_S.gguf", (fp16.textEncoder as? ModelSpec.HuggingFace)?.filename)

        val wan22 = presets[2]
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
