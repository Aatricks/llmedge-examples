package com.example.llmedgeexample

import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import io.aatricks.llmedge.SmolLM
import io.aatricks.llmedge.SmolLM.InferenceParams
import io.aatricks.llmedge.SmolLM.ThinkingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import java.util.Locale

class HuggingFaceDemoActivity : AppCompatActivity() {

    private val llm = SmolLM()

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
            val parsedReasoningBudget = reasoningBudgetText.takeIf { it.isNotEmpty() }?.toIntOrNull()

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

            if (isUiActive()) {
                textStatus.text = "Starting download..."
                textOutput.text = ""
                button.isEnabled = false
            }

            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.Default) {
                        llm.close()
                    }

                    val heapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
                    val safeContext = when {
                        heapMb <= 256 -> 2_048L
                        heapMb <= 384 -> 4_096L
                        else -> 6_144L
                    }
                    val safeThreads = min(max(Runtime.getRuntime().availableProcessors(), 1), 4)

                    val safeParams = InferenceParams(
                        storeChats = false,
                        numThreads = safeThreads,
                        contextSize = safeContext,
                        thinkingMode = if (disableThinkingChecked) ThinkingMode.DISABLED else ThinkingMode.DEFAULT,
                        reasoningBudget = parsedReasoningBudget,
                    )

                    val result = llm.loadFromHuggingFace(
                        context = this@HuggingFaceDemoActivity,
                        modelId = modelId,
                        revision = revision,
                        filename = filename,
                        params = safeParams,
                        forceDownload = forceDownload.isChecked,
                        onProgress = { downloaded, total ->
                            runOnUiThread {
                                if (isUiActive()) {
                                    textStatus.text = formatProgress(downloaded, total)
                                }
                            }
                        }
                    )
                    val cacheStatus = if (result.fromCache) "(cached)" else "(downloaded)"
                    val resolvedLabel =
                        if (result.aliasApplied && !result.requestedModelId.equals(result.modelId, ignoreCase = true)) {
                            "${result.modelId} (alias for ${result.requestedModelId})"
                        } else {
                            result.modelId
                        }
                    if (isUiActive()) {
                        textStatus.text = buildString {
                            append("Model ready $cacheStatus from $resolvedLabel: ${result.file.name}\n")
                            append("Heap allowance: ${heapMb}MB\n")
                            append("Context: ${safeParams.contextSize} tokens | Threads: ${safeParams.numThreads}\n")
                            append("Thinking enabled: ${llm.isThinkingEnabled()} (budget=${llm.getReasoningBudget()})")
                        }
                    }

                    llm.addSystemPrompt("You are a concise assistant running on-device.")

                    val response = withContext(Dispatchers.Default) {
                        llm.getResponse("List two quick facts about running GGUF models on Android.")
                    }
                    val metrics = llm.getLastGenerationMetrics()
                    if (isUiActive()) {
                        textOutput.text = buildString {
                            appendLine("Response:")
                            appendLine()
                            appendLine(response.trim())
                            appendLine()
                            appendLine(
                                "Metrics: tokens=${metrics.tokenCount}, " +
                                    "throughput=${"%.2f".format(Locale.US, metrics.tokensPerSecond)} tok/s, " +
                                    "duration=${"%.2f".format(Locale.US, metrics.elapsedSeconds)} s",
                            )
                            appendLine("Thinking mode: ${llm.getThinkingMode()} (budget=${llm.getReasoningBudget()})")
                            appendLine()
                            append("Stored at: ${result.file.absolutePath}")
                        }
                    }
                } catch (t: Throwable) {
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

    override fun onDestroy() {
        super.onDestroy()
        llm.close()
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
}
