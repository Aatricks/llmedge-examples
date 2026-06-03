package com.example.llmedgeexample.demo.conversion

import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.example.llmedgeexample.common.bindEdge
import com.example.llmedgeexample.common.formatDownloadProgress
import io.aatricks.llmedge.model.ConversionPrecision
import io.aatricks.llmedge.model.ModelSpec
import io.aatricks.llmedge.text.TextModelOptions
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Demonstrates on-device safetensors → GGUF conversion (Track B / Phase B2).
 *
 * Resolves a Hugging Face *safetensors* model via [ModelSpec.safetensors]: the library downloads the
 * model dir (config.json + model.safetensors + tokenizer files), converts it to a GGUF on-device —
 * baking the GPT2-BPE tokenizer and optionally quantizing to Q4_K_M — caches the result, then loads and
 * generates from it. No pre-converted GGUF and no host tooling required.
 *
 * The UI is built programmatically to keep the demo self-contained.
 */
class SafetensorsConversionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SafetensorsConvertDemo"
        // SmolLM-135M ships single-file safetensors + a GPT2-BPE tokenizer (pre id "smollm"), so it is
        // a small, fully-supported target for the v1 on-device converter (Llama arch + BPE tokenizer).
        private const val DEFAULT_REPO = "HuggingFaceTB/SmolLM-135M"
        private const val DEFAULT_PRE = "smollm"
    }

    private val edge by lazy(LazyThreadSafetyMode.NONE) { bindEdge(this, this, lifecycleScope) }

    private lateinit var status: TextView
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "On-device Safetensors → GGUF"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val repoInput = EditText(this).apply {
            hint = "HF repo id, or an absolute /path to a local model dir"
            setText(DEFAULT_REPO)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val preInput = EditText(this).apply {
            hint = "tokenizer.ggml.pre id (e.g. smollm)"
            setText(DEFAULT_PRE)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val quantize = CheckBox(this).apply {
            text = "Quantize to Q4_K_M (smaller; uncheck for F16)"
            isChecked = true
        }
        val runButton = Button(this).apply { text = "Convert & Run" }
        status = TextView(this).apply { setPadding(0, pad, 0, pad / 2) }
        output = TextView(this).apply { setTextIsSelectable(true) }

        listOf<android.view.View>(
            label("Safetensors repo"), repoInput,
            label("Pre-tokenizer id"), preInput,
            quantize, runButton, status, output,
        ).forEach {
            root.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(ScrollView(this).apply { addView(root) })

        runButton.setOnClickListener {
            val repoId = repoInput.text.toString().trim()
            val pre = preInput.text.toString().trim()
            if (repoId.isEmpty() || pre.isEmpty()) {
                setStatus("Provide a repo id and a pre-tokenizer id.")
                return@setOnClickListener
            }
            val precision = if (quantize.isChecked) ConversionPrecision.Q4_K_M else ConversionPrecision.F16
            runButton.isEnabled = false
            output.text = ""
            convertAndRun(repoId, pre, precision) { runButton.isEnabled = true }
        }
    }

    private fun convertAndRun(
        repoId: String,
        pre: String,
        precision: ConversionPrecision,
        onDone: () -> Unit,
    ) {
        setStatus("Resolving + converting $repoId ($precision)…")
        lifecycleScope.launch {
            try {
                // 1. safetensors spec -> download dir (HF) or use a local dir -> on-device convert
                //    (+ quantize) -> cached GGUF. An absolute path is treated as a local model dir.
                val spec =
                    if (repoId.startsWith("/")) {
                        ModelSpec.safetensorsLocal(path = repoId, precision = precision, tokenizerPre = pre)
                    } else {
                        ModelSpec.safetensors(repoId = repoId, precision = precision, tokenizerPre = pre)
                    }
                val gguf = edge.models.prefetch(spec) { progress ->
                    val msg = formatDownloadProgress(progress.downloadedBytes, progress.totalBytes)
                    runOnUiThread { if (isUiActive()) setStatus("Downloading model dir…\n$msg") }
                }
                if (isUiActive()) {
                    setStatus("Converted → ${gguf.name} (${gguf.length() / (1024 * 1024)} MB)\nGenerating…")
                }

                // 2. Load the converted GGUF and generate (CPU; safe on emulator/low-end).
                val response = withContext(Dispatchers.IO) {
                    edge.text.generate(
                        prompt = "The capital of France is",
                        model = ModelSpec.localFile(gguf),
                        systemPrompt = "You are a concise assistant running on-device.",
                        options = TextModelOptions(useVulkan = false, useFlashAttention = false),
                    )
                }
                val metrics = edge.text.getLastGenerationMetrics()
                if (isUiActive()) {
                    output.text = buildString {
                        appendLine("Converted GGUF: ${gguf.absolutePath}")
                        appendLine("Size: ${gguf.length() / (1024 * 1024)} MB  •  precision: $precision")
                        appendLine()
                        appendLine("Response:")
                        appendLine(response.trim())
                        metrics?.let {
                            appendLine()
                            append(
                                "Metrics: ${it.tokenCount} tokens, " +
                                    "${"%.1f".format(Locale.US, it.tokensPerSecond)} tok/s",
                            )
                        }
                    }
                    setStatus("Done.")
                }
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "conversion demo failed", t)
                if (isUiActive()) {
                    setStatus("Failed: ${t.message}")
                }
            } finally {
                if (isUiActive()) onDone()
            }
        }
    }

    private fun label(text: String): TextView =
        TextView(this).apply {
            this.text = text
            setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
        }

    private fun setStatus(text: String) {
        if (::status.isInitialized) status.text = text
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun isUiActive(): Boolean =
        lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && !isFinishing && !isDestroyed
}
