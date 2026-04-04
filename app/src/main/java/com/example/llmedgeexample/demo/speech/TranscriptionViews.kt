package com.example.llmedgeexample.demo.speech

import android.app.Activity
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.example.llmedgeexample.R

internal data class TranscriptionViews(
    val statusLabel: TextView,
    val transcriptionText: TextView,
    val transcriptionScroll: ScrollView,
    val progressBar: ProgressBar,
    val recordButton: Button,
    val stopButton: Button,
    val transcribeButton: Button,
    val downloadButton: Button,
    val languageLabel: TextView,
) {
    companion object {
        fun bind(activity: Activity): TranscriptionViews =
            TranscriptionViews(
                statusLabel = activity.findViewById(R.id.transcriptionStatusLabel),
                transcriptionText = activity.findViewById(R.id.transcriptionText),
                transcriptionScroll = activity.findViewById(R.id.transcriptionScroll),
                progressBar = activity.findViewById(R.id.transcriptionProgressBar),
                recordButton = activity.findViewById(R.id.btnStartRecording),
                stopButton = activity.findViewById(R.id.btnStopRecording),
                transcribeButton = activity.findViewById(R.id.btnTranscribe),
                downloadButton = activity.findViewById(R.id.btnDownloadModel),
                languageLabel = activity.findViewById(R.id.detectedLanguageLabel),
            )
    }
}
