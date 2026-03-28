package com.example.llmedgeexample

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.aatricks.llmedge.model.ModelSpec
import io.aatricks.llmedge.text.runtime.SmolLM
import io.aatricks.llmedge.text.TextGenerationRequest
import io.aatricks.llmedge.text.TextStreamEvent
import io.aatricks.llmedge.util.MemoryMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale

/**
 * Activity demonstrating loading and running a model from local assets.
 * 
 * Features:
 * - Blocking and streaming generation modes
 * - Memory metrics display
 * - Timeout handling for generation
 */
class LocalAssetDemoActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LocalAssetDemoActivity"
        private const val GENERATION_TIMEOUT_MS = 60_000L // 60 seconds
    }

    private val edge by lazy(LazyThreadSafetyMode.NONE) { bindEdge(this, this, lifecycleScope) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_local_asset_demo)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val output = findViewById<TextView>(R.id.output)

        lifecycleScope.launch {
            try {
                val before = MemoryMetrics.snapshot(this@LocalAssetDemoActivity)
                withContext(Dispatchers.Main) {
                    output.text = "Memory before:\n" +
                        before.toPretty(this@LocalAssetDemoActivity) + "\n\n"
                }

                val modelPath = copyAssetIfNeeded("YourModel.gguf")
                val modelFile = File(modelPath)
                
                withContext(Dispatchers.Main) {
                    output.append(
                        "Preparing model...\nPath: $modelPath\nExists: ${modelFile.exists()}\n" +
                        "Size: ${if (modelFile.exists()) "${modelFile.length() / (1024 * 1024)} MB" else "n/a"}\n\n"
                    )
                }

                if (!modelFile.exists()) {
                    withContext(Dispatchers.Main) {
                        output.append("\nModel file not found. Please add 'YourModel.gguf' to assets.\n")
                    }
                    return@launch
                }

                // Log memory state
                logDemoMemoryState(TAG, "Before model load")

                // Check available memory
                val availMemMB = availableMemoryMb()
                if (availMemMB < 1500) {
                    withContext(Dispatchers.Main) {
                        output.append("Warning: Low memory (${availMemMB}MB). May experience issues.\n\n")
                    }
                }

                // Blocking generation
                val modelSpec = ModelSpec.localFile(modelPath)
                val safeTextOptions = io.aatricks.llmedge.text.TextModelOptions(
                    useVulkan = false, // Force CPU instead of OpenCL/Vulkan for this demo path.
                    useFlashAttention = false,
                )

                try {
                    val blocking = withContext(Dispatchers.IO) {
                        withTimeoutOrNull(GENERATION_TIMEOUT_MS) {
                            edge.text.generate(
                                prompt = "Say 'hello from llmedge'.",
                                model = modelSpec,
                                options = safeTextOptions,
                                maxTokens = 50,
                            )
                        }
                    }
                    
                    if (blocking == null) {
                        withContext(Dispatchers.Main) {
                            output.append("Blocking generation timed out.\n\n")
                        }
                    } else {
                        val blockingMetrics = edge.text.getLastGenerationMetrics()
                        val afterBlocking = MemoryMetrics.snapshot(this@LocalAssetDemoActivity)
                        withContext(Dispatchers.Main) {
                            output.append("Blocking response:\n\n$blocking\n\n")
                            blockingMetrics?.let { output.append("Blocking metrics: ${formatMetrics(it)}\n\n") }
                            output.append("After blocking:\n" + afterBlocking.toPretty(this@LocalAssetDemoActivity) + "\n\nStreaming response:\n\n")
                        }
                    }
                } catch (oom: OutOfMemoryError) {
                    android.util.Log.e(TAG, "OOM during blocking generation", oom)
                    logDemoMemoryState(TAG, "OOM error")
                    withContext(Dispatchers.Main) {
                        output.append("\nBlocking failed: Out of memory\n")
                    }
                    return@launch
                } catch (t: Throwable) {
                    android.util.Log.e(TAG, "Blocking generation failed", t)
                    withContext(Dispatchers.Main) {
                        output.append("\nBlocking failed: ${t.message}\n")
                    }
                }

                // Streaming generation
                val sb = StringBuilder()
                val streamRequest = TextGenerationRequest(
                    prompt = "Write a short haiku about Android.",
                    model = modelSpec,
                    options = safeTextOptions,
                    maxTokens = 100 // Limit for demo
                )
                
                val ok = try {
                    withContext(Dispatchers.IO) {
                        withTimeoutOrNull(GENERATION_TIMEOUT_MS) {
                            edge.text.stream(streamRequest).collect { event ->
                                if (event is TextStreamEvent.Chunk) {
                                    sb.append(event.value)
                                    runOnUiThread {
                                        val currentText = output.text.toString()
                                        val prefix = currentText.substringBefore("Streaming response:") + "Streaming response:\n\n"
                                        output.text = prefix + sb.toString()
                                    }
                                }
                            }
                            true
                        } != null
                    }
                } catch (oom: OutOfMemoryError) {
                    android.util.Log.e(TAG, "OOM during streaming", oom)
                    false
                } catch (t: Throwable) {
                    android.util.Log.e(TAG, "Streaming failed", t)
                    false
                }
                
                val streamingMetrics = if (ok) edge.text.getLastGenerationMetrics() else null
                val afterStream = MemoryMetrics.snapshot(this@LocalAssetDemoActivity)
                
                logDemoMemoryState(TAG, "After streaming")
                
                withContext(Dispatchers.Main) {
                    output.append(if (ok) "\n\n[done]\n\n" else "\n\n[stream failed or timed out]\n\n")
                    streamingMetrics?.let { output.append("Streaming metrics: ${formatMetrics(it)}\n\n") }
                    output.append("After streaming:\n" + afterStream.toPretty(this@LocalAssetDemoActivity))
                }
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "Activity demo failed", t)
                withContext(Dispatchers.Main) {
                    output.append("\n\nError: ${t.message}")
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun copyAssetIfNeeded(assetName: String): String {
        val outFile = File(filesDir, assetName)
        if (!outFile.exists()) {
            try {
                assets.open(assetName).use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Could not copy asset $assetName: ${e.message}")
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
