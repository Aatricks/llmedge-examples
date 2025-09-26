package com.example.llmedgeexample

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.aatricks.llmedge.SmolLM
import io.aatricks.llmedge.SmolLM.InferenceParams
import io.aatricks.llmedge.util.MemoryMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val llm = SmolLM()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val output = findViewById<TextView>(R.id.output)
        findViewById<android.widget.Button>(R.id.btnOpenRag).setOnClickListener {
            startActivity(Intent(this, RagActivity::class.java))
        }

        lifecycleScope.launch {
            val before = MemoryMetrics.snapshot(this@MainActivity)
            withContext(Dispatchers.Main) {
                output.text = "Vulkan enabled: ${llm.isVulkanEnabled()}\n" +
                        before.toPretty(this@MainActivity) + "\n\n"
            }

            val modelPath = copyAssetIfNeeded("YourModel.gguf")
            val modelFile = File(modelPath)
            withContext(Dispatchers.Main) {
                output.append("Preparing model...\nPath: $modelPath\nExists: ${modelFile.exists()}\nSize: ${if (modelFile.exists()) "${modelFile.length() / (1024 * 1024)} MB" else "n/a"}\n\n")
            }

            try {
                // Load model (IO thread)
                llm.load(
                    modelPath = modelPath,
                    params = InferenceParams(
                        numThreads = Runtime.getRuntime().availableProcessors().coerceAtMost(4),
                        // Smaller context for initial test; increase once stable
                        contextSize = 8192L,
                    )
                )
                val afterLoad = MemoryMetrics.snapshot(this@MainActivity)
                withContext(Dispatchers.Main) {
                    output.append("Loaded OK.\n\nAfter load:\n" + afterLoad.toPretty(this@MainActivity) + "\n\n")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    output.append("\nLoad failed: ${t.message}\n")
                }
                return@launch
            }

            llm.addSystemPrompt("You are a helpful assistant.")

            // Blocking example (quick prompt)
            try {
                val blocking = withContext(Dispatchers.Default) {
                    llm.getResponse("Say 'hello from llmedge'.")
                }
                val blockingMetrics = llm.getLastGenerationMetrics()
                val afterBlocking = MemoryMetrics.snapshot(this@MainActivity)
                withContext(Dispatchers.Main) {
                    output.append("Blocking response:\n\n$blocking\n\n")
                    output.append("Blocking metrics: ${formatMetrics(blockingMetrics)}\n\n")
                    output.append("After blocking:\n" + afterBlocking.toPretty(this@MainActivity) + "\n\nStreaming response:\n\n")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    output.append("\nBlocking failed: ${t.message}\n")
                }
            }

            // Streaming example off the main thread; post UI updates, with a safety timeout
            val sb = StringBuilder()
            val ok = withContext(Dispatchers.Default) {
                withTimeoutOrNull(30000L) { // 30s safety timeout for demo
                    llm.getResponseAsFlow("Write a short haiku about Android.")
                        .collect { piece ->
                            if (piece != "[EOG]") {
                                sb.append(piece)
                                withContext(Dispatchers.Main) {
                                    output.text = output.text.toString().substringBefore("Streaming response:") +
                                            "Streaming response:\n\n" + sb.toString()
                                }
                            }
                        }
                } != null
            }
            val streamingMetrics = if (ok) llm.getLastGenerationMetrics() else null
            val afterStream = MemoryMetrics.snapshot(this@MainActivity)
            withContext(Dispatchers.Main) {
                output.append(if (ok) "\n\n[done]\n\n" else "\n\n[stream timed out]\n\n")
                streamingMetrics?.let {
                    output.append("Streaming metrics: ${formatMetrics(it)}\n\n")
                }
                output.append("After streaming:\n" + afterStream.toPretty(this@MainActivity))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        llm.close()
    }

    private fun copyAssetIfNeeded(assetName: String): String {
        val outFile = File(filesDir, assetName)
        if (!outFile.exists()) {
            assets.open(assetName).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return outFile.absolutePath
    }

    private fun formatMetrics(metrics: SmolLM.GenerationMetrics): String {
        val throughput = String.format(Locale.US, "%.2f", metrics.tokensPerSecond)
        val duration = String.format(Locale.US, "%.2f", metrics.elapsedSeconds)
        return "tokens=${metrics.tokenCount} | $throughput tok/s | $duration s"
    }
}