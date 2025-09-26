package com.example.llmedgeexample

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.aatricks.llmedge.SmolLM
import io.aatricks.llmedge.SmolLM.InferenceParams
import io.aatricks.llmedge.rag.RAGEngine
import io.aatricks.llmedge.rag.EmbeddingConfig
import io.aatricks.llmedge.rag.TextSplitter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class RagActivity : AppCompatActivity() {
    private val llm = SmolLM()
    private var rag: RAGEngine? = null
    private var selectedPdf: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rag)

        val pickBtn = findViewById<Button>(R.id.btnPick)
        val indexBtn = findViewById<Button>(R.id.btnIndex)
        val askBtn = findViewById<Button>(R.id.btnAsk)
        val previewBtn = findViewById<Button>(R.id.btnPreview)
        val question = findViewById<EditText>(R.id.inputQuestion)
        val status = findViewById<TextView>(R.id.txtStatus)
        val answer = findViewById<TextView>(R.id.txtAnswer)
        val contextView = findViewById<TextView>(R.id.txtContext)

        pickBtn.setOnClickListener { pickPdf() }
        indexBtn.setOnClickListener { indexSelectedPdf(status) }
        askBtn.setOnClickListener { askQuestion(question.text.toString(), status, answer, contextView) }
        previewBtn.setOnClickListener { previewRetrieval(question.text.toString(), status, answer, contextView) }

        lifecycleScope.launch {
            status.text = "Loading LLM..."
            val modelPath = copyAssetIfNeeded("YourModel.gguf")
            try {
                llm.load(
                    modelPath,
                    InferenceParams(
                        contextSize = 8192L,
                        numThreads = Runtime.getRuntime().availableProcessors().coerceAtMost(4),
                        storeChats = false,
                        temperature = 0.2f
                    )
                )
                llm.addSystemPrompt("You are a helpful assistant that only uses the provided context.")
                // If you use bge-small-en-v1.5, prefer enabling token_type_ids and last_hidden_state
                rag = RAGEngine(
                    context = this@RagActivity,
                    smolLM = llm,
                    splitter = TextSplitter(chunkSize = 600, chunkOverlap = 120),
                    embeddingConfig = EmbeddingConfig(
                        // Adjust to your assets path/model choice
                        modelAssetPath = "embeddings/all-minilm-l6-v2/model.onnx",
                        tokenizerAssetPath = "embeddings/all-minilm-l6-v2/tokenizer.json",
                        // For BGE-like models set to true and last_hidden_state
                        useTokenTypeIds = false,
                        outputTensorName = "sentence_embedding"
                    )
                ).also { it.init() }
                status.text = "LLM ready. Pick a PDF to index."
            } catch (t: Throwable) {
                status.text = "LLM load failed: ${t.message}"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        llm.close()
    }

    private fun pickPdf() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        startActivityForResult(intent, REQ_PICK_PDF)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_PDF && resultCode == Activity.RESULT_OK) {
            selectedPdf = data?.data
            selectedPdf?.let { uri ->
                // Keep read permission across restarts
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
            }
            findViewById<TextView>(R.id.txtStatus).text =
                "Selected: ${selectedPdf?.let { getDisplayName(it) } ?: "(none)"}"
        }
    }

    private fun indexSelectedPdf(status: TextView) {
        val uri = selectedPdf ?: run {
            status.text = "Pick a PDF first"
            return
        }
        status.text = "Indexing..."
        lifecycleScope.launch {
            try {
                val count = rag?.indexPdf(uri) ?: 0
                status.text = if (count > 0) {
                    "Indexed $count chunks. Ask a question."
                } else {
                    "No text extracted (0 chunks). If the PDF is scanned (images), OCR is not enabled in this demo. Try a text-based PDF."
                }
            } catch (t: Throwable) {
                status.text = "Index failed: ${t.message}"
            }
        }
    }

    private fun askQuestion(q: String, status: TextView, answer: TextView, contextView: TextView) {
        if (q.isBlank()) {
            status.text = "Enter a question"
            return
        }
        status.text = "Retrieving and answering..."
        answer.text = ""
        lifecycleScope.launch {
            try {
                // Build context separately to populate the panel
                rag?.contextFor(q)
                refreshContextPanel(contextView)
                val a = rag?.ask(q) ?: "RAG not ready"
                val metrics = llm.getLastGenerationMetrics()
                answer.text = a
                status.text = "Done\n" + formatMetrics(metrics)
            } catch (t: Throwable) {
                status.text = "Ask failed: ${t.message}"
            }
        }
    }

    private fun previewRetrieval(q: String, status: TextView, answer: TextView, contextView: TextView) {
        if (q.isBlank()) {
            status.text = "Enter a question"
            return
        }
        status.text = "Previewing retrieval..."
        answer.text = ""
        lifecycleScope.launch {
            try {
                val prev = rag?.retrievalPreview(q, topK = 5) ?: "(no engine)"
                answer.text = "Top-K preview:\n\n$prev"
                // Also build and display the exact context that will be sent
                rag?.contextFor(q)
                val hadContext = refreshContextPanel(contextView)
                if (!hadContext) {
                    // Inform user to try "Ask" which may include top-1 fallback
                    answer.append("\n\n(note) Context filtered out by score thresholds; tap 'Ask' to try top-1 fallback.")
                }
                status.text = "Preview ready"
            } catch (t: Throwable) {
                status.text = "Preview failed: ${t.message}"
            }
        }
    }

    private fun refreshContextPanel(contextView: TextView): Boolean {
        val ctx = rag?.getLastContext().orEmpty()
        val has = ctx.isNotBlank()
        contextView.text = if (!has) "(no context)" else ctx
        return has
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

    private fun getDisplayName(uri: Uri): String {
        return contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else uri.lastPathSegment ?: "PDF"
        } ?: (uri.lastPathSegment ?: "PDF")
    }

    companion object { private const val REQ_PICK_PDF = 42 }

    private fun formatMetrics(metrics: SmolLM.GenerationMetrics): String {
        val throughput = String.format(Locale.US, "%.2f", metrics.tokensPerSecond)
        val duration = String.format(Locale.US, "%.2f", metrics.elapsedSeconds)
        return "tokens=${metrics.tokenCount} | $throughput tok/s | $duration s"
    }
}