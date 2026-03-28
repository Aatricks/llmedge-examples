package com.example.llmedgeexample.demo.tools

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.example.llmedgeexample.R
import com.example.llmedgeexample.common.*
import io.aatricks.llmedge.model.ModelSpec
import io.aatricks.llmedge.text.TextModelOptions
import io.aatricks.llmedge.text.runtime.SmolLM
import io.aatricks.llmedge.tools.DeviceToolFactory
import io.aatricks.llmedge.tools.ToolResult
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Activity demonstrating deterministic device-tool orchestration for small LLMs.
 */
class ToolCallingDemoActivity : AppCompatActivity() {
    private val edge by lazy(LazyThreadSafetyMode.NONE) { bindEdge(this, this, lifecycleScope) }

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
                    val modelOptions =
                        TextModelOptions(
                            temperature = 0.0f,
                            thinkingMode = SmolLM.ThinkingMode.DISABLED,
                            reasoningBudget = 0,
                        )
                    val modelFile =
                        edge.models.prefetch(modelSpec) { progress ->
                            runOnUiThread {
                                if (isUiActive()) {
                                    textStatus.text =
                                        formatDownloadProgress(
                                            downloadedBytes = progress.downloadedBytes,
                                            totalBytes = progress.totalBytes,
                                            prefix = "Downloading model",
                                            decimals = 1,
                                        )
                                }
                            }
                        }

                    val localModel = ModelSpec.localFile(modelFile)
                    val toolFactory = DeviceToolFactory(this@ToolCallingDemoActivity)
                    val allowActions = switchAllowActions.isChecked
                    val intent = ToolCallingDemoPlanner.analyze(prompt)

                    textStatus.text =
                        if (intent.needsDeterministicTools) {
                            "Model ready at ${modelFile.name}. Running deterministic tool step..."
                        } else {
                            "Model ready at ${modelFile.name}. Generating direct response..."
                        }
                    val finishLabel =
                        if (intent.needsDeterministicTools) {
                            "Finished: deterministic tool synthesis"
                        } else {
                            "Finished: direct response"
                        }

                    val finalResponse =
                        if (intent.needsDeterministicTools) {
                            appendEvent(textEvents, "Started: $prompt")
                            appendEvent(textEvents, "Deterministic plan: ${describeIntent(intent)}")
                            val snapshot = executeDeterministicPlan(toolFactory, intent, allowActions, textEvents)
                            textStatus.text = "Generating final answer from tool results..."
                            val generated =
                                edge.text.generate(
                                    prompt = ToolCallingDemoPlanner.buildSynthesisPrompt(prompt, snapshot),
                                    model = localModel,
                                    systemPrompt =
                                        "You are a concise Android assistant. Answer using only verified tool results.",
                                    options = modelOptions,
                                ).trim()
                            if (ToolCallingDemoPlanner.shouldUseFallback(generated, intent, snapshot)) {
                                appendEvent(
                                    textEvents,
                                    "Using deterministic fallback response because the model answer did not match the verified tool results.",
                                )
                                ToolCallingDemoPlanner.buildFallbackResponse(snapshot)
                            } else {
                                generated
                            }
                        } else {
                            appendEvent(textEvents, "Started: $prompt")
                            edge.text.generate(
                                prompt = prompt,
                                model = localModel,
                                systemPrompt =
                                    "You are a concise Android assistant. Reply in plain text only.",
                                options = modelOptions,
                            ).trim()
                        }

                    textResponse.text =
                        finalResponse.ifBlank {
                            "No user-visible final answer was produced. Check the verified tool results above."
                        }
                    val metrics = edge.text.getLastGenerationMetrics()
                    textStatus.text =
                        buildString {
                            append(finishLabel)
                            metrics?.let {
                                append(
                                    " | ${it.tokenCount} tokens @ ${
                                        String.format(Locale.US, "%.2f", it.tokensPerSecond)
                                    } tok/s",
                                )
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

    private suspend fun executeDeterministicPlan(
        toolFactory: DeviceToolFactory,
        intent: DeviceToolDemoIntent,
        allowActions: Boolean,
        textEvents: TextView,
    ): DeterministicToolSnapshot {
        var timeText: String? = null
        var timestamp: String? = null
        var batteryText: String? = null
        var batteryPercent: Int? = null
        var isCharging: Boolean? = null
        var browserStatus: BrowserActionStatus = BrowserActionStatus.NotRequested

        if (intent.wantsCurrentTime) {
            val result = runTool(toolFactory.createGetTimeTool().name, textEvents) {
                toolFactory.createGetTimeTool().handler(buildJsonObject { })
            }
            timeText = result.text
            timestamp = result.data["timestamp"]?.jsonPrimitive?.contentOrNull
        }

        if (intent.wantsBatteryStatus) {
            val result = runTool(toolFactory.createGetBatteryStatusTool().name, textEvents) {
                toolFactory.createGetBatteryStatusTool().handler(buildJsonObject { })
            }
            batteryText = result.text
            batteryPercent = result.data["batteryPercent"]?.jsonPrimitive?.intOrNull
            isCharging = result.data["isCharging"]?.jsonPrimitive?.booleanOrNull
            appendEvent(
                textEvents,
                "Structured battery values: batteryPercent=${batteryPercent ?: "unavailable"}, isCharging=${isCharging ?: "unavailable"}",
            )
        }

        when (val decision = ToolCallingDemoPlanner.decideBrowserAction(intent, allowActions, batteryPercent)) {
            BrowserActionDecision.NotRequested -> Unit

            is BrowserActionDecision.Execute -> {
                val result =
                    runTool(toolFactory.createOpenBrowserTool().name, textEvents) {
                        toolFactory.createOpenBrowserTool().handler(
                            buildJsonObject {
                                put("url", decision.url)
                            },
                        )
                    }
                browserStatus =
                    if (result.isError) {
                        BrowserActionStatus.Failed(decision.url, result.text)
                    } else {
                        BrowserActionStatus.Executed(decision.url, result.text)
                    }
            }

            is BrowserActionDecision.Skip -> {
                browserStatus = BrowserActionStatus.Skipped(decision.url, decision.reason)
                appendEvent(textEvents, "Browser action skipped for ${decision.url}: ${decision.reason}")
            }
        }

        return DeterministicToolSnapshot(
            timeText = timeText,
            timestamp = timestamp,
            batteryText = batteryText,
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            browserStatus = browserStatus,
        )
    }

    private suspend fun runTool(
        toolName: String,
        textEvents: TextView,
        execute: suspend () -> ToolResult,
    ): ToolResult {
        appendEvent(textEvents, "Deterministic tool requested: $toolName {}")
        appendEvent(textEvents, "Executing: $toolName")
        val result = execute()
        appendEvent(textEvents, "Tool result: $toolName -> ${result.text}")
        return result
    }

    private fun describeIntent(intent: DeviceToolDemoIntent): String =
        buildList {
            if (intent.wantsCurrentTime) {
                add("time")
            }
            if (intent.wantsBatteryStatus) {
                add("battery")
            }
            intent.browserUrl?.let { url ->
                val threshold =
                    intent.batteryThresholdPercent?.let { percent -> ", threshold>${percent}%" }.orEmpty()
                add("browser(url=$url$threshold)")
            }
        }.ifEmpty { listOf("no device tools detected") }.joinToString(", ")

    private fun isUiActive(): Boolean =
        lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
            !isFinishing &&
            !isDestroyed
}
