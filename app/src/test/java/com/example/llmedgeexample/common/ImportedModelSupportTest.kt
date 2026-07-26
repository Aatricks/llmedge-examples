package com.example.llmedgeexample.common

import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportedModelSupportTest {
    @Test
    fun `compatible gguf is copied under a safe app-owned filename`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val imported =
            ImportedModelSupport.copyToAppStorage(
                context = context,
                displayName = "../Wan-Finetune.GGUF",
                input = ByteArrayInputStream(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte(), 1)),
                internalNamePrefix = "wan-",
            )

        assertEquals("Wan-Finetune.GGUF", imported.displayName)
        assertTrue(imported.file.isFile)
        assertTrue(imported.file.name.startsWith("wan-Wan-Finetune_"))
        assertTrue(imported.file.canonicalPath.startsWith(context.filesDir.canonicalPath))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non gguf import is rejected before copying`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        ImportedModelSupport.copyToAppStorage(
            context = context,
            displayName = "model.safetensors",
            input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
        )
    }
}
