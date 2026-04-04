package com.example.llmedgeexample.demo.speech

import android.app.Activity
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.example.llmedgeexample.R

internal data class TTSViews(
    val statusLabel: TextView,
    val textInput: EditText,
    val logOutput: TextView,
    val logScroll: ScrollView,
    val progressBar: ProgressBar,
    val progressLabel: TextView,
    val generateButton: Button,
    val playButton: Button,
    val saveButton: Button,
    val downloadButton: Button,
    val timingLabel: TextView,
) {
    companion object {
        fun bind(activity: Activity): TTSViews =
            TTSViews(
                statusLabel = activity.findViewById(R.id.ttsStatusLabel),
                textInput = activity.findViewById(R.id.ttsTextInput),
                logOutput = activity.findViewById(R.id.ttsLogOutput),
                logScroll = activity.findViewById(R.id.ttsLogScroll),
                progressBar = activity.findViewById(R.id.ttsProgressBar),
                progressLabel = activity.findViewById(R.id.ttsProgressLabel),
                generateButton = activity.findViewById(R.id.btnGenerate),
                playButton = activity.findViewById(R.id.btnPlay),
                saveButton = activity.findViewById(R.id.btnSave),
                downloadButton = activity.findViewById(R.id.btnDownloadBarkModel),
                timingLabel = activity.findViewById(R.id.ttsTiming),
            )
    }
}
