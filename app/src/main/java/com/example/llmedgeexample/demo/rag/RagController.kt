package com.example.llmedgeexample.demo.rag

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.llmedgeexample.common.availableMemoryMb
import com.example.llmedgeexample.common.getOpenableDisplayName
import com.example.llmedgeexample.common.logDemoMemoryState
import io.aatricks.llmedge.LLMEdge
import io.aatricks.llmedge.rag.RAGSession
import io.aatricks.llmedge.rag.TextSplitter
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * First use downloads both RAG artifacts, so a connectivity failure is the expected
 * first-run error. Reporting the raw exception hides what the user has to do about it.
 */
internal fun ragInitFailureMessage(failure: Throwable): String {
    val seen = mutableSetOf<Throwable>()
    var cause: Throwable? = failure
    while (cause != null && seen.add(cause)) {
        if (cause is UnknownHostException ||
            cause is ConnectException ||
            cause is SocketTimeoutException ||
            cause is NoRouteToHostException
        ) {
            return "No network. First use downloads the MiniLM embedding model (~90 MB) and the " +
                "Qwen3-0.6B language model (~400 MB). Connect to the internet and reopen this demo."
        }
        cause = cause.cause
    }
    return "LLM load failed: ${failure.message}"
}

internal class RagController(
    private val activity: AppCompatActivity,
    private val scope: LifecycleCoroutineScope,
    private val edge: LLMEdge,
    private val views: RagViews,
    private val embeddingProvisioner: MiniLmEmbeddingProvisioner = MiniLmEmbeddingProvisioner(),
) {
    companion object {
        private const val TAG = "RagActivity"
        private const val REQ_PICK_PDF = 42
    }

    private var rag: RAGSession? = null
    private var selectedPdf: Uri? = null

    fun initialize() {
        scope.launch {
            val availableMemoryMb = activity.availableMemoryMb()
            android.util.Log.i(TAG, "Available memory: ${availableMemoryMb}MB")

            views.statusView.text =
                if (availableMemoryMb < 1500) {
                    "Warning: Low memory (${availableMemoryMb}MB). Close other apps."
                } else {
                    "Loading LLMEdge RAG session..."
                }

            try {
                activity.logDemoMemoryState(TAG, "Before LLM load")
                rag =
                    withContext(Dispatchers.IO) {
                        val embeddingConfig =
                            embeddingProvisioner.prepare(activity) { status ->
                                activity.runOnUiThread {
                                    views.statusView.text = status
                                }
                            }
                        edge.rag.createSession(
                            splitter = TextSplitter(chunkSize = 400, chunkOverlap = 80),
                            embeddingConfig = embeddingConfig,
                        ).also { it.init() }
                    }
                activity.logDemoMemoryState(TAG, "After LLM and RAG init")
                views.statusView.text = "LLM ready. Pick a PDF to index."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (oom: OutOfMemoryError) {
                android.util.Log.e(TAG, "OOM loading LLM", oom)
                activity.logDemoMemoryState(TAG, "OOM error")
                views.statusView.text = "Out of memory. Close other apps and restart."
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "LLM load failed", t)
                views.statusView.text = ragInitFailureMessage(t)
            }
        }
    }

    fun pickPdf() {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
            }
        activity.startActivityForResult(intent, REQ_PICK_PDF)
    }

    fun handleActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ): Boolean {
        if (requestCode != REQ_PICK_PDF || resultCode != Activity.RESULT_OK) {
            return false
        }

        selectedPdf = data?.data
        selectedPdf?.let { uri ->
            try {
                activity.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Throwable) {
            }
        }
        views.selectedPdf(selectedPdf?.let(::displayName))
        return true
    }

    fun indexSelectedPdf() {
        val uri = selectedPdf ?: run {
            views.statusView.text = "Pick a PDF first"
            return
        }
        views.statusView.text = "Indexing..."
        scope.launch {
            try {
                activity.logDemoMemoryState(TAG, "Before indexing")
                val count =
                    withContext(Dispatchers.IO) {
                        rag?.indexPdf(uri) ?: 0
                    }
                activity.logDemoMemoryState(TAG, "After indexing")
                views.statusView.text =
                    if (count > 0) {
                        "Indexed $count chunks. Ask a question."
                    } else {
                        "No text extracted (0 chunks). If the PDF is scanned (images), OCR is not enabled. Try a text-based PDF."
                    }
            } catch (oom: OutOfMemoryError) {
                android.util.Log.e(TAG, "OOM during indexing", oom)
                views.statusView.text = "Out of memory during indexing. Try a smaller PDF."
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "Index failed", t)
                views.statusView.text = "Index failed: ${t.message}"
            }
        }
    }

    fun askQuestion() {
        val question = views.questionInput.text.toString()
        if (question.isBlank()) {
            views.statusView.text = "Enter a question"
            return
        }

        views.statusView.text = "Retrieving and answering..."
        views.answerView.text = ""
        scope.launch {
            try {
                activity.logDemoMemoryState(TAG, "Before RAG query")
                withContext(Dispatchers.IO) {
                    rag?.contextFor(question)
                }
                refreshContextPanel()
                val answer =
                    withContext(Dispatchers.IO) {
                        rag?.ask(question) ?: "RAG not ready"
                    }
                val metrics = rag?.getLastGenerationMetrics()
                activity.logDemoMemoryState(TAG, "After RAG query")
                views.answerView.text = answer
                views.statusView.text = "Done\n" + (metrics?.let(views::formatMetrics).orEmpty())
            } catch (oom: OutOfMemoryError) {
                android.util.Log.e(TAG, "OOM during RAG query", oom)
                views.statusView.text = "Out of memory. Try a shorter question."
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "Ask failed", t)
                views.statusView.text = "Ask failed: ${t.message}"
            }
        }
    }

    fun previewRetrieval() {
        val question = views.questionInput.text.toString()
        if (question.isBlank()) {
            views.statusView.text = "Enter a question"
            return
        }

        views.statusView.text = "Previewing retrieval..."
        views.answerView.text = ""
        scope.launch {
            try {
                val preview =
                    withContext(Dispatchers.IO) {
                        rag?.retrievalPreview(question, topK = 5) ?: "(no engine)"
                    }
                views.answerView.text = "Top-K preview:\n\n$preview"
                withContext(Dispatchers.IO) {
                    rag?.contextFor(question)
                }
                val hadContext = refreshContextPanel()
                if (!hadContext) {
                    views.answerView.append(
                        "\n\n(note) Context filtered out by score thresholds; tap 'Ask' to try top-1 fallback.",
                    )
                }
                views.statusView.text = "Preview ready"
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "Preview failed", t)
                views.statusView.text = "Preview failed: ${t.message}"
            }
        }
    }

    fun clear() {
        rag = null
    }

    private fun refreshContextPanel(): Boolean =
        views.setContext(rag?.engine?.getLastContext().orEmpty())

    private fun displayName(uri: Uri): String =
        activity.getOpenableDisplayName(uri, "PDF")
}
