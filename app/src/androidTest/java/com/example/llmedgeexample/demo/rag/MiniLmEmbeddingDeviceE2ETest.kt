package com.example.llmedgeexample.demo.rag

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import io.aatricks.llmedge.rag.EmbeddingProvider
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
    fun packagedEmbeddingAssetsInitializeAndEncodeOnDevice() = runBlocking {
        assumeTrue(
            "Enable with -e llmedge.miniLmDownloadE2E 1",
            InstrumentationRegistry.getArguments().getString("llmedge.miniLmDownloadE2E") == "1",
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config =
            withTimeout(15 * 60_000L) {
                MiniLmEmbeddingProvisioner().prepare(context)
            }
        assertEquals("embeddings/all-minilm-l6-v2/model.onnx", config.modelAssetPath)
        assertEquals("embeddings/all-minilm-l6-v2/tokenizer.json", config.tokenizerAssetPath)
        context.assets.open(config.modelAssetPath).use { model ->
            assertTrue(model.available() > 80L * 1024L * 1024L)
        }
        context.assets.open(config.tokenizerAssetPath).use { tokenizer ->
            assertTrue(tokenizer.available() > 400L * 1024L)
        }

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
