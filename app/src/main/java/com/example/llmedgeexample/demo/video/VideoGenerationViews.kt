package com.example.llmedgeexample.demo.video

import android.app.Activity
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import com.example.llmedgeexample.R

internal data class VideoGenerationViews(
    val promptInput: EditText,
    val negativePromptInput: EditText,
    val widthInput: EditText,
    val heightInput: EditText,
    val framesInput: EditText,
    val fpsInput: EditText,
    val stepsInput: EditText,
    val cfgInput: EditText,
    val seedInput: EditText,
    val flowShiftInput: EditText,
    val modelSpinner: Spinner,
    val selectModelButton: Button,
    val modelLabel: TextView,
    val clearModelButton: Button,
    val easyCacheToggle: Switch,
    val samplerSpinner: Spinner,
    val schedulerSpinner: Spinner,
    val selectLoraButton: Button,
    val loraLabel: TextView,
    val clearLoraButton: Button,
    val selectTaehvButton: Button,
    val taehvLabel: TextView,
    val clearTaehvButton: Button,
    val generateButton: Button,
    val cancelButton: Button,
    val selectImageButton: Button,
    val clearImageButton: Button,
    val saveGifButton: Button,
    val shareLogsButton: Button,
    val logPathLabel: TextView,
    val progressBar: ProgressBar,
    val progressLabel: TextView,
    val previewImage: ImageView,
    val metricsLabel: TextView,
    val i2vImageLabel: TextView,
    val i2vPreviewImage: ImageView,
    val i2vStrengthSeekBar: SeekBar,
    val i2vStrengthLabel: TextView,
) {
    companion object {
        fun bind(activity: Activity): VideoGenerationViews =
            VideoGenerationViews(
                promptInput = activity.findViewById(R.id.videoPromptInput),
                negativePromptInput = activity.findViewById(R.id.videoNegativePromptInput),
                widthInput = activity.findViewById(R.id.videoWidthInput),
                heightInput = activity.findViewById(R.id.videoHeightInput),
                framesInput = activity.findViewById(R.id.videoFramesInput),
                fpsInput = activity.findViewById(R.id.videoFpsInput),
                stepsInput = activity.findViewById(R.id.videoStepsInput),
                cfgInput = activity.findViewById(R.id.videoCfgInput),
                seedInput = activity.findViewById(R.id.videoSeedInput),
                flowShiftInput = activity.findViewById(R.id.videoFlowShiftInput),
                modelSpinner = activity.findViewById(R.id.videoModelSpinner),
                selectModelButton = activity.findViewById(R.id.btnSelectVideoModel),
                modelLabel = activity.findViewById(R.id.videoModelLabel),
                clearModelButton = activity.findViewById(R.id.btnClearVideoModel),
                easyCacheToggle = activity.findViewById(R.id.videoEasyCacheToggle),
                samplerSpinner = activity.findViewById(R.id.samplerSpinner),
                schedulerSpinner = activity.findViewById(R.id.schedulerSpinner),
                selectLoraButton = activity.findViewById(R.id.btnSelectLora),
                loraLabel = activity.findViewById(R.id.loraLabel),
                clearLoraButton = activity.findViewById(R.id.btnClearLora),
                selectTaehvButton = activity.findViewById(R.id.btnSelectTaehv),
                taehvLabel = activity.findViewById(R.id.taehvLabel),
                clearTaehvButton = activity.findViewById(R.id.btnClearTaehv),
                generateButton = activity.findViewById(R.id.btnGenerateVideo),
                cancelButton = activity.findViewById(R.id.btnCancelVideo),
                selectImageButton = activity.findViewById(R.id.btnSelectImage),
                clearImageButton = activity.findViewById(R.id.btnClearImage),
                saveGifButton = activity.findViewById(R.id.btnSaveGif),
                shareLogsButton = activity.findViewById(R.id.btnShareLogs),
                logPathLabel = activity.findViewById(R.id.logPathLabel),
                progressBar = activity.findViewById(R.id.videoProgressBar),
                progressLabel = activity.findViewById(R.id.videoProgressLabel),
                previewImage = activity.findViewById(R.id.videoPreview),
                metricsLabel = activity.findViewById(R.id.videoMetricsLabel),
                i2vImageLabel = activity.findViewById(R.id.i2vImageLabel),
                i2vPreviewImage = activity.findViewById(R.id.i2vPreviewImage),
                i2vStrengthSeekBar = activity.findViewById(R.id.i2vStrengthSeekBar),
                i2vStrengthLabel = activity.findViewById(R.id.i2vStrengthLabel),
            )
    }
}
