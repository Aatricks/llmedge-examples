package com.example.llmedgeexample.samples

import android.content.Context
import android.graphics.Bitmap
import io.aatricks.llmedge.LLMEdge
import io.aatricks.llmedge.image.ImageGenerationRequest
import io.aatricks.llmedge.image.VideoGenerationRequest
import io.aatricks.llmedge.model.ModelSpec
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect

object SimpleGenerator {
    suspend fun generate(
        context: Context,
        prompt: String,
        modelId: String = "wan/wan2.1-t2v-1.3B",
        isVideo: Boolean = true,
        outputDir: File = File(context.filesDir, "generations"),
    ): File {
        if (!outputDir.exists()) outputDir.mkdirs()

        val edge =
            LLMEdge.create(
                context = context,
                scope = CoroutineScope(SupervisorJob()),
            )
        try {
            if (isVideo) {
                var frames: List<Bitmap> = emptyList()
                edge.image.generateVideo(
                    VideoGenerationRequest(
                        prompt = prompt,
                        model = ModelSpec.huggingFace(repoId = modelId),
                    ),
                ).collect { event ->
                    if (event is io.aatricks.llmedge.image.GenerationStreamEvent.Completed) {
                        frames = event.frames
                    }
                }
                val outputFile = File(outputDir, "video_${System.currentTimeMillis()}.png")

                frames.firstOrNull()?.let { bitmap ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputFile.outputStream())
                }
                return outputFile
            }

            val bitmap =
                edge.image.generate(
                    ImageGenerationRequest(
                        prompt = prompt,
                        model = ModelSpec.huggingFace(repoId = modelId),
                    ),
                )
            val outputFile = File(outputDir, "image_${System.currentTimeMillis()}.png")
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputFile.outputStream())
            return outputFile
        } finally {
            edge.close()
        }
    }
}
