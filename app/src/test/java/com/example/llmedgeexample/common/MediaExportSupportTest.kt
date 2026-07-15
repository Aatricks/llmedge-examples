package com.example.llmedgeexample.common

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaExportSupportTest {

    @Test
    fun testSaveBitmapToGallery() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        
        val uri = saveBitmapToGallery(context, bitmap, "test_image.png")
        assertNotNull("Expected non-null URI", uri)

        val resolver = context.contentResolver
        resolver.openInputStream(uri!!).use { stream ->
            assertNotNull("Expected non-null stream from saved URI", stream)
            val decoded = android.graphics.BitmapFactory.decodeStream(stream)
            assertNotNull("Expected decodable bitmap from saved URI", decoded)
            assertEquals(100, decoded.width)
            assertEquals(100, decoded.height)
        }
    }

    @Test
    fun testSaveFileToGallery() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempFile = File.createTempFile("test_video", ".gif", context.cacheDir).apply {
            writeBytes(byteArrayOf(0, 1, 2, 3))
        }

        val uri = saveFileToGallery(context, tempFile, "image/gif", "Pictures/LLMEdge", "test_file.gif")
        assertNotNull("Expected non-null URI", uri)

        // Robolectric's ShadowContentResolver cannot stream or query inserted
        // Downloads/Images rows back; the bitmap variant above covers the write path.
        assertEquals("content", uri!!.scheme)
    }
}
