package com.example.llmedgeexample.common

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class ImportedModelStorageDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "devicetest-"

    @After
    fun removeSlotFiles() {
        importedModelDir().listFiles().orEmpty().filter { it.name.startsWith(prefix) }.forEach(File::delete)
    }

    @Test
    fun replacingAnImportReclaimsThePreviousCopyOnDevice() {
        val first =
            ImportedModelSupport.copyToAppStorage(
                context = context,
                displayName = "first.gguf",
                input = ByteArrayInputStream(ggufPayload(1)),
                internalNamePrefix = prefix,
                expectedSizeBytes = PAYLOAD_BYTES.toLong(),
            )
        assertTrue(first.file.isFile)
        assertEquals(PAYLOAD_BYTES.toLong(), first.file.length())

        val second =
            ImportedModelSupport.copyToAppStorage(
                context = context,
                displayName = "second.gguf",
                input = ByteArrayInputStream(ggufPayload(2)),
                internalNamePrefix = prefix,
                expectedSizeBytes = PAYLOAD_BYTES.toLong(),
            )

        assertNotEquals(first.file, second.file)
        assertFalse(first.file.exists())
        assertEquals(PAYLOAD_BYTES.toLong(), second.file.length())
        assertEquals(1, slotFiles().size)
        assertEquals(PAYLOAD_BYTES.toLong(), slotFiles().sumOf(File::length))
    }

    @Test
    fun clearingAnImportDeletesTheAppOwnedCopy() {
        val imported =
            ImportedModelSupport.copyToAppStorage(
                context = context,
                displayName = "clearable.gguf",
                input = ByteArrayInputStream(ggufPayload(3)),
                internalNamePrefix = prefix,
                expectedSizeBytes = PAYLOAD_BYTES.toLong(),
            )

        assertTrue(ImportedModelSupport.deleteFromAppStorage(context, imported.file))
        assertFalse(imported.file.exists())
        assertTrue(slotFiles().isEmpty())
    }

    @Test
    fun filesOutsideTheImportDirectoryAreNeverDeleted() {
        val cached = File(context.cacheDir, "${prefix}outsider.gguf")
        val sibling = File(context.filesDir, "${prefix}sibling.gguf")
        cached.writeBytes(ggufPayload(4))
        sibling.writeBytes(ggufPayload(5))
        try {
            assertFalse(ImportedModelSupport.deleteFromAppStorage(context, cached))
            assertFalse(
                ImportedModelSupport.deleteFromAppStorage(
                    context,
                    File(importedModelDir(), "../${sibling.name}"),
                ),
            )
            assertTrue(cached.exists())
            assertTrue(sibling.exists())
        } finally {
            cached.delete()
            sibling.delete()
        }
    }

    private fun importedModelDir(): File = File(context.filesDir, "imported-models")

    private fun slotFiles(): List<File> =
        importedModelDir().listFiles().orEmpty().filter { it.name.startsWith(prefix) }

    private fun ggufPayload(marker: Byte): ByteArray {
        val payload = ByteArray(PAYLOAD_BYTES) { index -> (index + marker).toByte() }
        payload[0] = 'G'.code.toByte()
        payload[1] = 'G'.code.toByte()
        payload[2] = 'U'.code.toByte()
        payload[3] = 'F'.code.toByte()
        payload[4] = marker
        return payload
    }

    private companion object {
        const val PAYLOAD_BYTES = 8 * 1024 * 1024
    }
}
