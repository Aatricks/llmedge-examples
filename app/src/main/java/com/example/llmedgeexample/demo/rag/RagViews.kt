package com.example.llmedgeexample.demo.rag

import android.app.Activity
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.example.llmedgeexample.R
import io.aatricks.llmedge.text.runtime.SmolLM
import java.util.Locale

internal data class RagViews(
    val pickButton: Button,
    val indexButton: Button,
    val askButton: Button,
    val previewButton: Button,
    val questionInput: EditText,
    val statusView: TextView,
    val answerView: TextView,
    val contextView: TextView,
) {
    companion object {
        fun bind(activity: Activity): RagViews =
            RagViews(
                pickButton = activity.findViewById(R.id.btnPick),
                indexButton = activity.findViewById(R.id.btnIndex),
                askButton = activity.findViewById(R.id.btnAsk),
                previewButton = activity.findViewById(R.id.btnPreview),
                questionInput = activity.findViewById(R.id.inputQuestion),
                statusView = activity.findViewById(R.id.txtStatus),
                answerView = activity.findViewById(R.id.txtAnswer),
                contextView = activity.findViewById(R.id.txtContext),
            )
    }

    fun selectedPdf(name: String?) {
        statusView.text = "Selected: ${name ?: "(none)"}"
    }

    fun setContext(contextText: String): Boolean {
        val hasContext = contextText.isNotBlank()
        contextView.text = if (hasContext) contextText else "(no context)"
        return hasContext
    }

    fun formatMetrics(metrics: SmolLM.GenerationMetrics): String {
        val throughput = String.format(Locale.US, "%.2f", metrics.tokensPerSecond)
        val duration = String.format(Locale.US, "%.2f", metrics.elapsedSeconds)
        return "tokens=${metrics.tokenCount} | $throughput tok/s | $duration s"
    }
}
