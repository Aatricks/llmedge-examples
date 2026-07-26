package com.example.llmedgeexample.demo.video

import io.aatricks.llmedge.image.diffusion.SampleMethod
import io.aatricks.llmedge.image.diffusion.Scheduler
import org.junit.Assert.assertFalse
import org.junit.Test

class VideoGenerationRequestFactoryTest {
    @Test
    fun `easy cache toggle is preserved in the video request`() {
        val request =
            VideoGenerationRequestFactory.create(
                VideoGenerationConfig(
                    prompt = "a dog running",
                    width = 256,
                    height = 256,
                    frames = 1,
                    fps = 8,
                    steps = 4,
                    cfgScale = 1.0f,
                    seed = -1L,
                    flowShift = 1.0f,
                    model = null,
                    vae = null,
                    textEncoder = null,
                    sampleMethod = SampleMethod.EULER,
                    scheduler = Scheduler.DEFAULT,
                    loraDirectory = null,
                    taehvPath = null,
                    initImage = null,
                    initImageStrength = 0.8f,
                    defaultLoraDirectory = null,
                    easyCacheEnabled = false,
                ),
            )

        assertFalse(request.easyCache.enabled)
    }
}
