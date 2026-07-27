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

    private fun ggufBytes(marker: Int): ByteArray =
        byteArrayOf(
            'G'.code.toByte(),
            'G'.code.toByte(),
            'U'.code.toByte(),
            'F'.code.toByte(),
            marker.toByte(),
        )
}
