package com.example.llmedgeexample.common

import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
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
        assertTrue(imported.file.name.matches(Regex("wan-[0-9a-f]{64}\\.gguf")))
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

    @Test
    fun `a new import replaces the previous model in the same slot`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefix = "replacement-${System.nanoTime()}-"
        val first =
            ImportedModelSupport.copyToAppStorage(
                context = context,
                displayName = "first.gguf",
                input = ByteArrayInputStream(ggufBytes(1)),
                internalNamePrefix = prefix,
            )

        val second =
            ImportedModelSupport.copyToAppStorage(
                context = context,
                displayName = "second.gguf",
                input = ByteArrayInputStream(ggufBytes(2)),
                internalNamePrefix = prefix,
            )

        assertNotEquals(first.file, second.file)
        assertFalse(first.file.exists())
        assertTrue(second.file.readBytes().contentEquals(ggufBytes(2)))
        assertEquals(
            1,
            second.file.parentFile
                ?.listFiles()
                .orEmpty()
                .count { it.name.startsWith(prefix) },
        )
    }

    @Test
    fun `a known oversized import is rejected before reading the source`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val input =
            object : InputStream() {
                var wasRead = false

                override fun read(): Int {
                    wasRead = true
                    return -1
                }
            }

        try {
            ImportedModelSupport.copyToAppStorage(
                context = context,
                displayName = "oversized.gguf",
                input = input,
                internalNamePrefix = "oversized-${System.nanoTime()}-",
                expectedSizeBytes = 1_024,
                availableBytesProvider = { 512 },
            )
            fail("Expected insufficient storage to reject the import")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("free"))
        }
        assertFalse(input.wasRead)
    }

    @Test
    fun `storage preflight does not overflow for extreme reported sizes`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val input =
            object : InputStream() {
                var wasRead = false

                override fun read(): Int {
                    wasRead = true
                    return -1
                }
            }

        try {
            ImportedModelSupport.copyToAppStorage(
                context = context,
                displayName = "extreme.gguf",
                input = input,
                internalNamePrefix = "extreme-${System.nanoTime()}-",
                expectedSizeBytes = Long.MAX_VALUE,
                availableBytesProvider = { Long.MAX_VALUE },
            )
            fail("Expected storage headroom to reject the import")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("free"))
        }
        assertFalse(input.wasRead)
    }

    /**
     * llmedge-examples#37: importing an all-in-one SD3 checkpoint into a preset that routes it to
     * `diffusion_model_path` silently double-loads the encoders, and only surfaces much later as a
     * generation-time worker crash.
     *
     * Whether a given file *is* a bundle is decided by `ModelFileValidator.requireDiffusionOnlyGguf`
     * and covered in the SDK's `GgufFileSummaryTest`, which can stub the native GGUF reader. Here
     * the native library is absent, so what is worth asserting is the guarantee that matters when
     * classification is unavailable: the import still proceeds rather than failing closed.
     */
    @Test
    fun `an import proceeds when the checkpoint cannot be classified`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val imported =
            ImportedModelSupport.copyToAppStorage(
                context = context,
                displayName = "sd3-medium-Q4_0.gguf",
                input = ByteArrayInputStream(ggufBytes(7)),
                internalNamePrefix = "sd3-${System.nanoTime()}-",
                requireDiffusionOnly = true,
            )

        assertTrue(imported.file.isFile)
        assertTrue(imported.describe(), imported.describe().contains("unreadable"))
    }

    private fun ggufBytes(marker: Int): ByteArray =
        byteArrayOf(
            'G'.code.toByte(),
            'G'.code.toByte(),
            'U'.code.toByte(),
            'F'.code.toByte(),
            marker.toByte(),
        )
}
