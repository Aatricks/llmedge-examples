package com.example.llmedgeexample.demo.rag

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import io.aatricks.llmedge.rag.EmbeddingProvider
import java.io.File
import java.net.URI
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class MiniLmEmbeddingDeviceE2ETest {
    @Test
    fun downloadedEmbeddingFilesInitializeAndEncodeOnDevice() = runBlocking {
        assumeTrue(
            "Enable with -e llmedge.miniLmDownloadE2E 1",
            InstrumentationRegistry.getArguments().getString("llmedge.miniLmDownloadE2E") == "1",
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config =
            withTimeout(15 * 60_000L) {
                MiniLmEmbeddingProvisioner().prepare(context)
            }
        val modelFile = File(URI(config.modelAssetPath))
        val tokenizerFile = File(URI(config.tokenizerAssetPath))
        assertTrue(modelFile.isFile)
        assertTrue(modelFile.length() > 80L * 1024L * 1024L)
        assertTrue(tokenizerFile.isFile)
        assertTrue(tokenizerFile.length() > 400L * 1024L)

        val provider = EmbeddingProvider(context, config)
        try {
            withTimeout(5 * 60_000L) {
                provider.init()
            }
            val embedding =
                withTimeout(5 * 60_000L) {
                    provider.encode("LLMEdge on-device embedding test")
                }
            assertEquals(384, embedding.size)
            assertTrue(embedding.all { it.isFinite() })
            assertTrue(embedding.any { it != 0.0f })
        } finally {
            provider.close()
        }
    }
}
