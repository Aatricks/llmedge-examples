package com.example.llmedgeexample.demo.image

import io.aatricks.llmedge.model.ModelSpec
import java.io.File
import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImageGenerationActivityTest {
    @Test
    fun `clearing an imported model deletes the app-owned copy`() {
        val controller = Robolectric.buildActivity(ImageGenerationActivity::class.java).setup()
        val activity = controller.get()
        val importedFile =
            File(activity.filesDir, "imported-models/test-current.gguf").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3, 4))
            }
        ImageGenerationActivity::class.java
            .getDeclaredField("selectedModelOverride")
            .apply { isAccessible = true }
            .set(activity, ModelSpec.localFile(importedFile))

        ImageGenerationActivity::class.java
            .getDeclaredMethod("clearImportedModel")
            .apply { isAccessible = true }
            .invoke(activity)

        assertFalse(importedFile.exists())
        controller.close()
    }

    @Test
    fun `clearing is ignored while request preparation owns the model`() {
        val controller = Robolectric.buildActivity(ImageGenerationActivity::class.java).setup()
        val activity = controller.get()
        val importedFile =
            File(activity.filesDir, "imported-models/busy-current.gguf").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3, 4))
            }
        ImageGenerationActivity::class.java
            .getDeclaredField("selectedModelOverride")
            .apply { isAccessible = true }
            .set(activity, ModelSpec.localFile(importedFile))
        ImageGenerationActivity::class.java
            .getDeclaredField("requestPreparationJob")
            .apply { isAccessible = true }
            .set(activity, Job())

        ImageGenerationActivity::class.java
            .getDeclaredMethod("clearImportedModel")
            .apply { isAccessible = true }
            .invoke(activity)

        assertTrue(importedFile.exists())
        controller.close()
    }
}
