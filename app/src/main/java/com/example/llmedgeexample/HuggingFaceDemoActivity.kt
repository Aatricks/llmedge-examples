package com.example.llmedgeexample

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import io.aatricks.llmedge.text.runtime.SmolLM
import io.aatricks.llmedge.model.ModelSpec
import io.aatricks.llmedge.text.TextModelOptions
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity demonstrating downloading and running custom models from Hugging Face.
 * 
 * Features:
 * - Custom model download with progress
 * - Configurable thinking mode and reasoning budget
 * - Memory-efficient text generation
 */
class HuggingFaceDemoActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "HuggingFaceDemoActivity"
        private const val BYTES_IN_MB = 1024L * 1024L
    }

    private val edge by lazy(LazyThreadSafetyMode.NONE) { bindEdge(this, this, lifecycleScope) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hugging_face_demo)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val inputModelId = findViewById<EditText>(R.id.inputModelId)
        val inputRevision = findViewById<EditText>(R.id.inputRevision)
        val inputFilename = findViewById<EditText>(R.id.inputFilename)
        val forceDownload = findViewById<CheckBox>(R.id.checkboxForceDownload)
        val disableThinking = findViewById<CheckBox>(R.id.checkboxDisableThinking)
        val inputReasoningBudget = findViewById<EditText>(R.id.inputReasoningBudget)
        val textStatus = findViewById<TextView>(R.id.textStatus)
        val textOutput = findViewById<TextView>(R.id.textOutput)
        val button = findViewById<Button>(R.id.btnDownloadAndRun)

        button.setOnClickListener {
            val modelId = inputModelId.text.toString().trim()
            val revision = inputRevision.text.toString().takeIf { it.isNotBlank() } ?: "main"
            val filename = inputFilename.text.toString().trim().takeIf { it.isNotEmpty() }
            val disableThinkingChecked = disableThinking.isChecked
            val reasoningBudgetText = inputReasoningBudget.text.toString().trim()
            val parsedReasoningBudget =
                    reasoningBudgetText.takeIf { it.isNotEmpty() }?.toIntOrNull()

            if (modelId.isEmpty()) {
                if (isUiActive()) {
                    textStatus.text = "Please provide a model repository name."
                }
                return@setOnClickListener
            }

            if (reasoningBudgetText.isNotEmpty() && parsedReasoningBudget == null) {
                if (isUiActive()) {
                    textStatus.text = "Reasoning budget must be an integer (e.g. 0, -1)."
                }
                return@setOnClickListener
            }

            // Check available memory before starting
            val availMemMB = getAvailableMemoryMB()
            if (availMemMB < 1500) {
                if (isUiActive()) {
                    textStatus.text = "Warning: Low memory (${availMemMB}MB). Close other apps."
                }
            }

            if (isUiActive()) {
                textStatus.text = "Starting download..."
                textOutput.text = ""
                button.isEnabled = false
            }

            lifecycleScope.launch {
                try {
                    // Log memory state
                    logMemoryState("Before download")
                    
                    // 1. Download/Ensure model is available
                    val modelSpec =
                        ModelSpec.huggingFace(
                            repoId = modelId,
                            filename = filename,
                            revision = revision,
                            forceDownload = forceDownload.isChecked,
                        )
                    val modelFile = edge.models.prefetch(modelSpec) { progress ->
                        val downloaded = progress.downloadedBytes
                        val total = progress.totalBytes
                            runOnUiThread {
                                if (isUiActive()) {
                                    textStatus.text = formatProgress(downloaded, total)
                                }
                            }
                        }

                    if (isUiActive()) {
                        textStatus.text = buildString {
                            append("Model ready: ${modelFile.name}\n")
                            append("Path: ${modelFile.absolutePath}\n")
                        }
                    }

                    logMemoryState("After download, before generation")

                    // 2. Generate Text
                    val thinkingMode = if (disableThinkingChecked)
                        SmolLM.ThinkingMode.DISABLED
                    else 
                        SmolLM.ThinkingMode.DEFAULT

                    // Use IO dispatcher for native JNI operations
                    val response = withContext(Dispatchers.IO) {
                        edge.text.generate(
                            prompt = "List two quick facts about running GGUF models on Android.",
                            model = ModelSpec.localFile(modelFile),
                            systemPrompt = "You are a concise assistant running on-device.",
                            options =
                                TextModelOptions(
                                    useVulkan = false,
                                    useFlashAttention = false,
                                    thinkingMode = thinkingMode,
                                    reasoningBudget = parsedReasoningBudget,
                                ),
                        )
                    }

                    val metrics = edge.text.getLastGenerationMetrics()
                    
                    logMemoryState("After generation")

                    if (isUiActive()) {
                        textOutput.text = buildString {
                            appendLine("Response:")
                            appendLine()
                            appendLine(response.trim())
                            appendLine()
                            metrics?.let {
                                appendLine(
                                    "Metrics: tokens=${it.tokenCount}, " +
                                    "throughput=${"%.2f".format(Locale.US, it.tokensPerSecond)} tok/s, " +
                                    "duration=${"%.2f".format(Locale.US, it.elapsedSeconds)} s"
                                )
                            }
                            appendLine("Thinking mode: $thinkingMode (budget=$parsedReasoningBudget)")
                            appendLine()
                            append("Stored at: ${modelFile.absolutePath}")
                        }
                    }
                } catch (oom: OutOfMemoryError) {
                    android.util.Log.e(TAG, "Out of memory", oom)
                    logMemoryState("OOM error")
                    if (isUiActive()) {
                        textStatus.text = "Out of memory. Try a smaller model or close other apps."
                        textOutput.text = ""
                    }
                } catch (t: Throwable) {
                    android.util.Log.e(TAG, "Failed", t)
                    if (isUiActive()) {
                        textStatus.text = "Failed: ${t.message}"
                        textOutput.text = ""
                    }
                } finally {
                    if (isUiActive()) {
                        button.isEnabled = true
                    }
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun formatProgress(downloaded: Long, total: Long?): String {
        val downloadedMb = downloaded / (1024.0 * 1024.0)
        val totalMb = total?.div(1024.0 * 1024.0)
        return if (totalMb != null && totalMb > 0) {
            "Downloading: ${String.format(Locale.US, "%.2f", downloadedMb)} MB / ${String.format(Locale.US, "%.2f", totalMb)} MB"
        } else {
            "Downloading: ${String.format(Locale.US, "%.2f", downloadedMb)} MB"
        }
    }

    private fun isUiActive(): Boolean =
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                    !isFinishing &&
                    !isDestroyed

    private fun getAvailableMemoryMB(): Long {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.availMem / BYTES_IN_MB
    }

    private fun logMemoryState(phase: String) {
        val runtime = Runtime.getRuntime()
        val heapUsed = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_IN_MB
        val heapMax = runtime.maxMemory() / BYTES_IN_MB

        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val systemAvail = memoryInfo.availMem / BYTES_IN_MB

        android.util.Log.i(TAG, "=== Memory: $phase ===")
        android.util.Log.i(TAG, "  Heap: ${heapUsed}MB / ${heapMax}MB max")
        android.util.Log.i(TAG, "  System: ${systemAvail}MB available")
    }
}
