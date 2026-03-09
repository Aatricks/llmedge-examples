package com.example.llmedgeexample

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.aatricks.llmedge.rag.EmbeddingConfig
import io.aatricks.llmedge.rag.RAGSession
import io.aatricks.llmedge.rag.TextSplitter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity demonstrating RAG (Retrieval Augmented Generation) capabilities.
 * 
 * Features:
 * - PDF document indexing
 * - Semantic search and retrieval
 * - Context-aware question answering
 * - Memory-efficient operation via LLMEdge
 */
class RagActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "RagActivity"
        private const val REQ_PICK_PDF = 42
        private const val BYTES_IN_MB = 1024L * 1024L
    }
    
    private val edge by lazy(LazyThreadSafetyMode.NONE) { bindEdge(this, this, lifecycleScope) }
    private var rag: RAGSession? = null
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
        askBtn.setOnClickListener {
            askQuestion(question.text.toString(), status, answer, contextView)
        }
        previewBtn.setOnClickListener {
            previewRetrieval(question.text.toString(), status, answer, contextView)
        }

        lifecycleScope.launch {
            // Check memory before loading
            val availMemMB = getAvailableMemoryMB()
            android.util.Log.i(TAG, "Available memory: ${availMemMB}MB")
            
            if (availMemMB < 1500) {
                withContext(Dispatchers.Main) {
                    status.text = "Warning: Low memory (${availMemMB}MB). Close other apps."
                }
            } else {
                withContext(Dispatchers.Main) {
                    status.text = "Loading LLMEdge RAG session..."
                }
            }
            
            try {
                logMemoryState("Before LLM load")
                rag =
                    withContext(Dispatchers.IO) {
                        edge.rag.createSession(
                            splitter = TextSplitter(chunkSize = 400, chunkOverlap = 80),
                            embeddingConfig =
                                EmbeddingConfig(
                                    modelAssetPath = "embeddings/all-minilm-l6-v2/model.onnx",
                                    tokenizerAssetPath = "embeddings/all-minilm-l6-v2/tokenizer.json",
                                    useTokenTypeIds = false,
                                    outputTensorName = "sentence_embedding",
                                ),
                        ).also { it.init() }
                    }
                
                logMemoryState("After LLM and RAG init")
                
                withContext(Dispatchers.Main) {
                    status.text = "LLM ready. Pick a PDF to index."
                }
            } catch (oom: OutOfMemoryError) {
                android.util.Log.e(TAG, "OOM loading LLM", oom)
                logMemoryState("OOM error")
                withContext(Dispatchers.Main) {
                    status.text = "Out of memory. Close other apps and restart."
                }
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "LLM load failed", t)
                withContext(Dispatchers.Main) {
                    status.text = "LLM load failed: ${t.message}"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        rag = null
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
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Throwable) {}
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
                logMemoryState("Before indexing")
                
                val count = withContext(Dispatchers.IO) {
                    rag?.indexPdf(uri) ?: 0
                }
                
                logMemoryState("After indexing")
                
                withContext(Dispatchers.Main) {
                    status.text = if (count > 0) {
                        "Indexed $count chunks. Ask a question."
                    } else {
                        "No text extracted (0 chunks). If the PDF is scanned (images), OCR is not enabled. Try a text-based PDF."
                    }
                }
            } catch (oom: OutOfMemoryError) {
                android.util.Log.e(TAG, "OOM during indexing", oom)
                withContext(Dispatchers.Main) {
                    status.text = "Out of memory during indexing. Try a smaller PDF."
                }
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "Index failed", t)
                withContext(Dispatchers.Main) {
                    status.text = "Index failed: ${t.message}"
                }
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
                logMemoryState("Before RAG query")
                
                // Build context separately to populate the panel
                withContext(Dispatchers.IO) {
                    rag?.contextFor(q)
                }
                withContext(Dispatchers.Main) {
                    refreshContextPanel(contextView)
                }
                
                val a = withContext(Dispatchers.IO) {
                    rag?.ask(q) ?: "RAG not ready"
                }
                val metrics = rag?.getLastGenerationMetrics()
                
                logMemoryState("After RAG query")
                
                withContext(Dispatchers.Main) {
                    answer.text = a
                    status.text = "Done\n" + (metrics?.let { formatMetrics(it) } ?: "")
                }
            } catch (oom: OutOfMemoryError) {
                android.util.Log.e(TAG, "OOM during RAG query", oom)
                withContext(Dispatchers.Main) {
                    status.text = "Out of memory. Try a shorter question."
                }
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "Ask failed", t)
                withContext(Dispatchers.Main) {
                    status.text = "Ask failed: ${t.message}"
                }
            }
        }
    }

    private fun previewRetrieval(
        q: String,
        status: TextView,
        answer: TextView,
        contextView: TextView
    ) {
        if (q.isBlank()) {
            status.text = "Enter a question"
            return
        }
        status.text = "Previewing retrieval..."
        answer.text = ""
        lifecycleScope.launch {
            try {
                val prev = withContext(Dispatchers.IO) {
                    rag?.retrievalPreview(q, topK = 5) ?: "(no engine)"
                }
                
                withContext(Dispatchers.Main) {
                    answer.text = "Top-K preview:\n\n$prev"
                }
                
                // Also build and display the exact context that will be sent
                withContext(Dispatchers.IO) {
                    rag?.contextFor(q)
                }
                
                withContext(Dispatchers.Main) {
                    val hadContext = refreshContextPanel(contextView)
                    if (!hadContext) {
                        answer.append(
                            "\n\n(note) Context filtered out by score thresholds; tap 'Ask' to try top-1 fallback."
                        )
                    }
                    status.text = "Preview ready"
                }
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "Preview failed", t)
                withContext(Dispatchers.Main) {
                    status.text = "Preview failed: ${t.message}"
                }
            }
        }
    }

    private fun refreshContextPanel(contextView: TextView): Boolean {
        val ctx = rag?.engine?.getLastContext().orEmpty()
        val has = ctx.isNotBlank()
        contextView.text = if (!has) "(no context)" else ctx
        return has
    }

    private fun getDisplayName(uri: Uri): String {
        return contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else uri.lastPathSegment ?: "PDF"
        } ?: (uri.lastPathSegment ?: "PDF")
    }

    private fun formatMetrics(metrics: io.aatricks.llmedge.text.runtime.SmolLM.GenerationMetrics): String {
        val throughput = String.format(Locale.US, "%.2f", metrics.tokensPerSecond)
        val duration = String.format(Locale.US, "%.2f", metrics.elapsedSeconds)
        return "tokens=${metrics.tokenCount} | $throughput tok/s | $duration s"
    }

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
