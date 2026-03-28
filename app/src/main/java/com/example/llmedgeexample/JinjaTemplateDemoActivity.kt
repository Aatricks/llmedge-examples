package com.example.llmedgeexample

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.aatricks.llmedge.runtime.CpuTopology
import io.aatricks.llmedge.text.runtime.SmolLM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class JinjaTemplateDemoActivity : AppCompatActivity() {

    companion object {
        private const val MODEL_ID = "unsloth/Qwen3-0.6B-GGUF"
        private const val MODEL_REVISION = "main"
        private const val MODEL_FILENAME = "Qwen3-0.6B-Q4_K_M.gguf"
        private const val PROMPT = "Explain in one short sentence why this demo uses a custom chat template."
        private const val BACKEND_LABEL = "CPU"
        private val JINJA_CHAT_TEMPLATE =
            """
            {%- for message in messages -%}
            {{- '<|im_start|>' + message.role + '\n' + message.content + '<|im_end|>\n' -}}
            {%- endfor -%}
            {%- if add_generation_prompt -%}
            {{- '<|im_start|>assistant\n' -}}
            {%- endif -%}
            """.trimIndent()
        private val PROMPT_THREADS =
            CpuTopology.getOptimalThreadCount(CpuTopology.TaskType.PROMPT_PROCESSING)
        private val GENERATION_THREADS =
            CpuTopology.getOptimalThreadCount(CpuTopology.TaskType.TOKEN_GENERATION)
        private const val FLASH_ATTN_ENABLED = false
    }

    private var smol: SmolLM? = null
    private var demoJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_jinja_template_demo)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val output = findViewById<TextView>(R.id.output)
        val runButton = findViewById<Button>(R.id.btnRunJinjaDemo)

        output.text =
            buildString {
                appendLine("This demo loads SmolLM with an explicit loop-based Jinja chat template override.")
                appendLine("It intentionally uses the same CPU-oriented baseline as the Hugging Face demo so the template path is the main variable.")
                appendLine()
                appendLine("Model source:")
                appendLine("$MODEL_ID ($MODEL_FILENAME @ $MODEL_REVISION)")
                appendLine()
                appendLine("Runtime config:")
                appendLine("Backend: $BACKEND_LABEL")
                appendLine("Prompt threads: $PROMPT_THREADS")
                appendLine("Generation threads: $GENERATION_THREADS")
                appendLine("Flash attention: $FLASH_ATTN_ENABLED")
                appendLine()
                appendLine("Template:")
                appendLine(JINJA_CHAT_TEMPLATE)
                appendLine()
                appendLine("The model is downloaded on first run and reused from the local cache afterwards.")
            }

        runButton.setOnClickListener {
            demoJob?.cancel()
            demoJob =
                lifecycleScope.launch {
                    runButton.isEnabled = false
                    output.text =
                        buildString {
                            appendLine("Preparing Jinja template demo...")
                            appendLine("Using the same CPU baseline as the Hugging Face demo for a fair comparison.")
                            appendLine()
                            appendLine("Runtime config:")
                            appendLine("Backend: $BACKEND_LABEL")
                            appendLine("Prompt threads: $PROMPT_THREADS")
                            appendLine("Generation threads: $GENERATION_THREADS")
                            appendLine("Flash attention: $FLASH_ATTN_ENABLED")
                            appendLine()
                            appendLine("Downloading model from Hugging Face if needed:")
                            appendLine("$MODEL_ID ($MODEL_FILENAME @ $MODEL_REVISION)")
                            appendLine()
                            appendLine("Template:")
                            appendLine(JINJA_CHAT_TEMPLATE)
                            appendLine()
                        }

                    try {
                        smol?.close()
                        smol = SmolLM(useVulkan = false)

                        val downloadResult =
                            withContext(Dispatchers.IO) {
                                smol!!.loadFromHuggingFace(
                                    context = this@JinjaTemplateDemoActivity,
                                    modelId = MODEL_ID,
                                    revision = MODEL_REVISION,
                                    filename = MODEL_FILENAME,
                                    params =
                                        SmolLM.InferenceParams(
                                            chatTemplate = JINJA_CHAT_TEMPLATE,
                                            useFlashAttn = FLASH_ATTN_ENABLED,
                                            numThreads = PROMPT_THREADS,
                                            generationThreads = GENERATION_THREADS,
                                        ),
                                    onProgress = { downloaded, total ->
                                        runOnUiThread {
                                            output.text =
                                                buildString {
                                                    appendLine("Preparing Jinja template demo...")
                                                    appendLine("Using the same CPU baseline as the Hugging Face demo for a fair comparison.")
                                                    appendLine()
                                                    appendLine("Runtime config:")
                                                    appendLine("Backend: $BACKEND_LABEL")
                                                    appendLine("Prompt threads: $PROMPT_THREADS")
                                                    appendLine("Generation threads: $GENERATION_THREADS")
                                                    appendLine("Flash attention: $FLASH_ATTN_ENABLED")
                                                    appendLine()
                                                    appendLine("Download:")
                                                    appendLine(
                                                        formatDownloadProgress(downloaded, total)
                                                    )
                                                    appendLine()
                                                    appendLine("Model:")
                                                    appendLine("$MODEL_ID ($MODEL_FILENAME @ $MODEL_REVISION)")
                                                    appendLine()
                                                    appendLine("Template:")
                                                    appendLine(JINJA_CHAT_TEMPLATE)
                                                }
                                        }
                                    },
                                )
                            }

                        withContext(Dispatchers.IO) {
                            smol!!.addSystemPrompt(
                                "You are a concise assistant. Answer plainly and mention chat templates only if asked.",
                            )
                        }

                        val response =
                            withContext(Dispatchers.IO) {
                                smol!!.getResponse(PROMPT, maxTokens = 96)
                            }
                        val metrics = smol!!.getLastGenerationMetrics()

                        output.text =
                            buildString {
                                appendLine("Jinja template override loaded successfully.")
                                appendLine("Model: ${downloadResult.file.absolutePath}")
                                appendLine("Downloaded from: $MODEL_ID")
                                appendLine("Backend: $BACKEND_LABEL")
                                appendLine("Prompt threads: $PROMPT_THREADS")
                                appendLine("Generation threads: $GENERATION_THREADS")
                                appendLine("Flash attention: $FLASH_ATTN_ENABLED")
                                appendLine()
                                appendLine("Template:")
                                appendLine(JINJA_CHAT_TEMPLATE)
                                appendLine()
                                appendLine("Prompt:")
                                appendLine(PROMPT)
                                appendLine()
                                appendLine("Response:")
                                appendLine(response.trim())
                                appendLine()
                                appendLine(
                                    "Metrics: tokens=${metrics.tokenCount}, " +
                                        "throughput=${"%.2f".format(Locale.US, metrics.tokensPerSecond)} tok/s, " +
                                        "duration=${"%.2f".format(Locale.US, metrics.elapsedSeconds)} s",
                                )
                                appendLine("Context used: ${smol!!.getContextLengthUsed()} tokens")
                            }
                    } catch (error: Throwable) {
                        output.append("\nDemo failed: ${error.message}")
                    } finally {
                        runButton.isEnabled = true
                    }
                }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        demoJob?.cancel()
        smol?.close()
        smol = null
        super.onDestroy()
    }
}
