package com.example.llmedgeexample.common

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpenableFileSupportTest {
    @Test
    fun `provider display name cannot escape the requested cache directory`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val uri = Uri.parse("content://provider/..%2F..%2Fescaped.safetensors")
        shadowOf(context.contentResolver).registerInputStream(
            uri,
            ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
        )

        val copied =
            context.copyOpenableToCache(
                uri = uri,
                subdirectory = "safe-cache",
                fallbackFileName = "fallback.safetensors",
                requiredSuffix = ".safetensors",
            )

        assertEquals(File(context.cacheDir, "safe-cache").canonicalFile, copied.parentFile?.canonicalFile)
        assertEquals("escaped.safetensors", copied.name)
    }

    @Test
    fun `failed replacement preserves the previous cached file`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val uri = Uri.parse("content://provider/model.safetensors")
        val target =
            File(context.cacheDir, "atomic-cache/model.safetensors").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(9, 8, 7, 6))
            }
        shadowOf(context.contentResolver).registerInputStream(
            uri,
            object : InputStream() {
                private var reads = 0

                override fun read(): Int =
                    when (reads++) {
                        0 -> 1
                        else -> throw IOException("source failed")
                    }
            },
        )

        try {
            context.copyOpenableToCache(
                uri = uri,
                subdirectory = "atomic-cache",
                fallbackFileName = "fallback.safetensors",
                requiredSuffix = ".safetensors",
            )
            fail("Expected the failing source to abort the copy")
        } catch (_: IOException) {
        }

        assertArrayEquals(byteArrayOf(9, 8, 7, 6), target.readBytes())
    }
}
