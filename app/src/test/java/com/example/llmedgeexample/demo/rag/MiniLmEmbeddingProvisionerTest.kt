package com.example.llmedgeexample.demo.rag

import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.net.URI
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MiniLmEmbeddingProvisionerTest {
    @Test
    fun `provisioner returns downloaded model and tokenizer as local embedding files`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val downloadDirectory = File(context.filesDir, "embedding-download-test").apply { mkdirs() }
        val statuses = mutableListOf<String>()
        val provisioner =
            MiniLmEmbeddingProvisioner(
                artifactResolver =
                    EmbeddingArtifactResolver { _, filename, onProgress ->
                        onProgress(50L, 100L)
                        when (filename) {
                            "onnx/model.onnx" ->
                                File(downloadDirectory, "model.onnx").apply { writeText("model") }
                            "tokenizer.json" ->
                                File(downloadDirectory, "tokenizer.json").apply { writeText("{}") }
                            else -> error("Unexpected embedding artifact: $filename")
                        }
                    },
            )

        val config = provisioner.prepare(context, statuses::add)

        assertEquals("model", File(URI(config.modelAssetPath)).readText())
        assertEquals("{}", File(URI(config.tokenizerAssetPath)).readText())
        assertTrue(statuses.any { it == "Downloading MiniLM model... 50%" })
        assertTrue(statuses.any { it == "Downloading MiniLM tokenizer... 50%" })
    }
}
