package com.example.llmedgeexample.samples

import android.content.Context
import android.graphics.Bitmap
import io.aatricks.llmedge.image.diffusion.StableDiffusion
import io.aatricks.llmedge.image.diffusion.GenerateParams
import io.aatricks.llmedge.image.diffusion.VideoGenerateParams
import io.aatricks.llmedge.image.diffusion.txt2vid
import java.io.File

object SimpleGenerator {
    suspend fun generate(
        context: Context,
        prompt: String,
        modelId: String = "wan/wan2.1-t2v-1.3B",
        isVideo: Boolean = true,
        outputDir: File = File(context.filesDir, "generations"),
    ): File {
        if (!outputDir.exists()) outputDir.mkdirs()

        val sd = StableDiffusion.load(context, modelId = modelId)
        try {
            if (isVideo) {
                val params = VideoGenerateParams(prompt = prompt)
                val frames = sd.txt2vid(params)
                val outputFile = File(outputDir, "video_${System.currentTimeMillis()}.png")

                frames.firstOrNull()?.let { bitmap ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputFile.outputStream())
                }
                return outputFile
            }

            val params = GenerateParams(prompt = prompt)
            val bitmap = sd.txt2img(params)
            val outputFile = File(outputDir, "image_${System.currentTimeMillis()}.png")
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputFile.outputStream())
            return outputFile
        } finally {
            sd.close()
        }
    }
}