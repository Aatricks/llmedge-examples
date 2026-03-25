package com.example.llmedgeexample

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import io.aatricks.llmedge.model.ModelSpec
import io.aatricks.llmedge.text.TextModelOptions
import io.aatricks.llmedge.text.runtime.SmolLM
import io.aatricks.llmedge.tools.DeviceToolFactory
import io.aatricks.llmedge.tools.ToolAgentEvent
import io.aatricks.llmedge.tools.ToolDecision
import io.aatricks.llmedge.tools.ToolKind
import io.aatricks.llmedge.tools.ToolPolicy
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Activity demonstrating structured tool calling through `edge.text.toolAgent(...)`.
 *
 * The demo downloads a user-selected GGUF model, creates a ToolAgent with built-in device tools,
 * streams tool events, and optionally allows action tools like `open_browser`.
 */
class ToolCallingDemoActivity : AppCompatActivity() {
    private val edge by lazy(LazyThreadSafetyMode.NONE) { bindEdge(this, this, lifecycleScope) }
    private val json by lazy(LazyThreadSafetyMode.NONE) { Json { prettyPrint = false } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tool_calling_demo)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val inputModelId = findViewById<EditText>(R.id.inputToolModelId)
        val inputFilename = findViewById<EditText>(R.id.inputToolFilename)
        val inputPrompt = findViewById<EditText>(R.id.inputToolPrompt)
        val switchAllowActions = findViewById<Switch>(R.id.switchAllowActionTools)
        val textStatus = findViewById<TextView>(R.id.textToolStatus)
        val textEvents = findViewById<TextView>(R.id.textToolEvents)
        val textResponse = findViewById<TextView>(R.id.textToolResponse)
        val buttonRun = findViewById<Button>(R.id.btnRunToolAgent)

        buttonRun.setOnClickListener {
            val modelId = inputModelId.text.toString().trim()
            val filename = inputFilename.text.toString().trim()
            val prompt = inputPrompt.text.toString().trim()

            when {
                modelId.isEmpty() -> {
                    textStatus.text = "Enter a Hugging Face model repo."
                    return@setOnClickListener
                }

                filename.isEmpty() -> {
                    textStatus.text = "Enter a GGUF filename."
                    return@setOnClickListener
                }

                prompt.isEmpty() -> {
                    textStatus.text = "Enter a prompt for the tool agent."
                    return@setOnClickListener
                }
            }

            buttonRun.isEnabled = false
            textEvents.text = ""
            textResponse.text = ""
            textStatus.text = "Preparing tool-calling run..."

            lifecycleScope.launch {
                try {
                    val modelSpec = ModelSpec.huggingFace(repoId = modelId, filename = filename)
                    val modelFile =
                        edge.models.prefetch(modelSpec) { progress ->
                            val downloadedMb = progress.downloadedBytes / (1024.0 * 1024.0)
                            val totalMb = progress.totalBytes?.div(1024.0 * 1024.0)
                            runOnUiThread {
                                if (isUiActive()) {
                                    textStatus.text =
                                        if (totalMb != null && totalMb > 0) {
                                            "Downloading model: ${
                                                String.format(Locale.US, "%.1f", downloadedMb)
                                            } / ${String.format(Locale.US, "%.1f", totalMb)} MB"
                                        } else {
                                            "Downloading model: ${
                                                String.format(Locale.US, "%.1f", downloadedMb)
                                            } MB"
                                        }
                                }
                            }
                        }

                    val tools = DeviceToolFactory(this@ToolCallingDemoActivity).createDefaultTools()
                    val allowActions = switchAllowActions.isChecked
                    val agent =
                        edge.text.toolAgent(
                            tools = tools,
                            model = ModelSpec.localFile(modelFile),
                            systemPrompt = "You are a concise Android assistant. Use tools only when they directly help.",
                            options =
                                TextModelOptions(
                                    temperature = 0.0f,
                                    thinkingMode = SmolLM.ThinkingMode.DEFAULT,
                                ),
                            policy =
                                ToolPolicy { request ->
                                    if (request.tool.kind == ToolKind.READ_ONLY || allowActions) {
                                        ToolDecision.Allow
                                    } else {
                                        ToolDecision.Deny("Enable 'Allow action tools' to run ${request.tool.name}.")
                                    }
                                },
                        )

                    withContext(Dispatchers.Main) {
                        textStatus.text = "Model ready at ${modelFile.name}. Running tool agent..."
                    }

                    agent.stream(prompt).collect { event ->
                        when (event) {
                            is ToolAgentEvent.Started -> {
                                textStatus.text = "Running tool agent..."
                                appendEvent(textEvents, "Started: ${event.message}")
                            }

                            is ToolAgentEvent.ToolCallRequested -> {
                                appendEvent(
                                    textEvents,
                                    "Tool requested: ${event.call.tool} ${formatJson(event.call.arguments)}",
                                )
                            }

                            is ToolAgentEvent.ToolApproved ->
                                appendEvent(textEvents, "Tool approved: ${event.call.tool}")

                            is ToolAgentEvent.ToolDenied ->
                                appendEvent(textEvents, "Tool denied: ${event.call.tool} (${event.reason})")

                            is ToolAgentEvent.ToolExecuting ->
                                appendEvent(textEvents, "Executing: ${event.call.tool}")

                            is ToolAgentEvent.ToolResultReceived ->
                                appendEvent(
                                    textEvents,
                                    "Tool result: ${event.call.tool} -> ${event.result.text}",
                                )

                            is ToolAgentEvent.TextChunk -> {
                                textResponse.append(event.value)
                            }

                            is ToolAgentEvent.Completed -> {
                                val metrics = edge.text.getLastGenerationMetrics()
                                textStatus.text =
                                    buildString {
                                        append("Finished: ${event.result.finishReason}")
                                        metrics?.let {
                                            append(
                                                " | ${it.tokenCount} tokens @ ${
                                                    String.format(Locale.US, "%.2f", it.tokensPerSecond)
                                                } tok/s",
                                            )
                                        }
                                    }
                            }

                            is ToolAgentEvent.Failed -> {
                                textStatus.text = "Failed: ${event.message}"
                                appendEvent(textEvents, "Failed: ${event.message}")
                            }
                        }
                    }
                } catch (t: Throwable) {
                    textStatus.text = "Failed: ${t.message}"
                    appendEvent(textEvents, "Error: ${t.message}")
                } finally {
                    if (isUiActive()) {
                        buttonRun.isEnabled = true
                    }
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun appendEvent(
        textView: TextView,
        message: String,
    ) {
        val existing = textView.text?.toString().orEmpty()
        textView.text =
            if (existing.isBlank()) {
                message
            } else {
                "$existing\n$message"
            }
    }

    private fun formatJson(value: JsonObject): String = json.encodeToString(JsonObject.serializer(), value)

    private fun isUiActive(): Boolean =
        lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
            !isFinishing &&
            !isDestroyed
}
