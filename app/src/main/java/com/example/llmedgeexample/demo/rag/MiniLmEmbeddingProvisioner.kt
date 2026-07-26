package com.example.llmedgeexample.demo.rag

import android.content.Context
import io.aatricks.llmedge.huggingface.HuggingFaceHub
import io.aatricks.llmedge.rag.EmbeddingConfig
import java.io.File

internal fun interface EmbeddingArtifactResolver {
    suspend fun resolve(
        context: Context,
        filename: String,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): File
}

internal class MiniLmEmbeddingProvisioner(
    private val artifactResolver: EmbeddingArtifactResolver = HuggingFaceEmbeddingArtifactResolver,
) {
    suspend fun prepare(
        context: Context,
        onStatus: (String) -> Unit = {},
    ): EmbeddingConfig {
        val modelFile =
            resolveArtifact(
                context = context,
                filename = "onnx/model.onnx",
                label = "MiniLM model",
                onStatus = onStatus,
            )
        val tokenizerFile =
            resolveArtifact(
                context = context,
                filename = "tokenizer.json",
                label = "MiniLM tokenizer",
                onStatus = onStatus,
            )
        return EmbeddingConfig.fromFiles(
            modelFile = modelFile,
            tokenizerFile = tokenizerFile,
            useTokenTypeIds = false,
            outputTensorName = "sentence_embedding",
        )
    }

    private suspend fun resolveArtifact(
        context: Context,
        filename: String,
        label: String,
        onStatus: (String) -> Unit,
    ): File {
        onStatus("Downloading $label...")
        return artifactResolver.resolve(context, filename) { downloaded, total ->
            if (total != null && total > 0L) {
                val percent = ((downloaded * 100L) / total).coerceIn(0L, 100L)
                onStatus("Downloading $label... $percent%")
            } else {
                onStatus("Downloading $label...")
            }
        }
    }
}

private object HuggingFaceEmbeddingArtifactResolver : EmbeddingArtifactResolver {
    private const val REPO_ID = "sentence-transformers/all-MiniLM-L6-v2"
    private const val REVISION = "1110a243fdf4706b3f48f1d95db1a4f5529b4d41"

    override suspend fun resolve(
        context: Context,
        filename: String,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): File =
        HuggingFaceHub.ensureRepoFileOnDisk(
            context = context,
            modelId = REPO_ID,
            revision = REVISION,
            filename = filename,
            allowedExtensions = listOf(".onnx", ".json"),
            preferSystemDownloader = true,
            onProgress = onProgress,
        ).file
}
