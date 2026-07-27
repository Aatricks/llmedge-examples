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
     */
    @Test
    fun `all-in-one checkpoint is rejected for a diffusion-only preset`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        try {
            ImportedModelSupport.copyToAppStorage(
                context = context,
                displayName = "sd3-medium-Q4_0.gguf",
                input = ByteArrayInputStream(
                    ggufFile(
                        "sd3",
                        listOf(
                            "model.diffusion_model.joint_blocks.0.weight",
                            "text_encoders.clip_l.transformer.weight",
                            "first_stage_model.decoder.conv_in.weight",
                        ),
                    ),
                ),
                internalNamePrefix = "sd3-${System.nanoTime()}-",
                requireDiffusionOnly = true,
            )
            fail("Expected an all-in-one checkpoint to be rejected")
        } catch (expected: IllegalArgumentException) {
            val message = expected.message.orEmpty()
            assertTrue(message, message.contains("all-in-one"))
            assertTrue(message, message.contains("text encoders"))
        }
    }

    @Test
    fun `all-in-one checkpoint is accepted when the preset expects one`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val imported =
            ImportedModelSupport.copyToAppStorage(
                context = context,
                displayName = "sd15-all-in-one.gguf",
                input = ByteArrayInputStream(
                    ggufFile("sd1", listOf("model.diffusion_model.a.weight", "first_stage_model.b.weight")),
                ),
                internalNamePrefix = "stable-diffusion-${System.nanoTime()}-",
                requireDiffusionOnly = false,
            )

        assertTrue(imported.file.isFile)
        assertTrue(imported.describe().contains("arch=sd1"))
    }

    @Test
    fun `diffusion-only checkpoint passes the diffusion-only check`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val imported =
            ImportedModelSupport.copyToAppStorage(
                context = context,
                displayName = "sd3_medium-Q4_0.gguf",
                input = ByteArrayInputStream(
                    ggufFile("sd3", listOf("model.diffusion_model.joint_blocks.0.weight")),
                ),
                internalNamePrefix = "sd3-${System.nanoTime()}-",
                requireDiffusionOnly = true,
            )

        assertTrue(imported.file.isFile)
        assertTrue(imported.describe().contains("DIFFUSION"))
    }

    @Test
    fun `an unparseable header does not block an otherwise valid import`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val imported =
            ImportedModelSupport.copyToAppStorage(
                context = context,
                displayName = "opaque.gguf",
                input = ByteArrayInputStream(ggufBytes(9)),
                internalNamePrefix = "opaque-${System.nanoTime()}-",
                requireDiffusionOnly = true,
            )

        assertTrue(imported.file.isFile)
        assertTrue(imported.describe().contains("unparseable"))
    }

    /** Minimal GGUF v3 header: magic, counts, one string metadata entry, then tensor infos. */
    private fun ggufFile(architecture: String, tensorNames: List<String>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun u32(value: Long) = repeat(4) { out.write(((value shr (it * 8)) and 0xFF).toInt()) }
        fun u64(value: Long) = repeat(8) { out.write(((value shr (it * 8)) and 0xFF).toInt()) }
        fun str(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            u64(bytes.size.toLong())
            out.write(bytes)
        }

        out.write("GGUF".toByteArray(Charsets.US_ASCII))
        u32(3)
        u64(tensorNames.size.toLong())
        u64(1)
        str("general.architecture")
        u32(8)
        str(architecture)
        tensorNames.forEach { name ->
            str(name)
            u32(1)
            u64(16)
            u32(0)
            u64(0)
        }
        return out.toByteArray()
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
