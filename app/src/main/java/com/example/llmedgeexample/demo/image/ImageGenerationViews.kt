package com.example.llmedgeexample.demo.image

import android.app.Activity
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import com.example.llmedgeexample.R

internal data class ImageGenerationViews(
    val promptInput: EditText,
    val negativePromptInput: EditText,
    val widthInput: EditText,
    val heightInput: EditText,
    val stepsInput: EditText,
    val cfgInput: EditText,
    val seedInput: EditText,
    val generateButton: Button,
    val cancelButton: Button,
    val progressBar: ProgressBar,
    val progressLabel: TextView,
    val previewImage: ImageView,
    val metricsLabel: TextView,
    val loraToggle: Switch,
    val modelPresetGroup: RadioGroup,
    val shareLogsButton: Button,
    val logPathLabel: TextView,
) {
    companion object {
        fun bind(activity: Activity): ImageGenerationViews =
            ImageGenerationViews(
                promptInput = activity.findViewById(R.id.videoPromptInput),
                negativePromptInput = activity.findViewById(R.id.imageNegativePromptInput),
                widthInput = activity.findViewById(R.id.imageWidthInput),
                heightInput = activity.findViewById(R.id.imageHeightInput),
                stepsInput = activity.findViewById(R.id.imageStepsInput),
                cfgInput = activity.findViewById(R.id.imageCfgInput),
                seedInput = activity.findViewById(R.id.imageSeedInput),
                generateButton = activity.findViewById(R.id.btnGenerateVideo),
                cancelButton = activity.findViewById(R.id.btnCancelVideo),
                progressBar = activity.findViewById(R.id.videoProgressBar),
                progressLabel = activity.findViewById(R.id.videoProgressLabel),
                previewImage = activity.findViewById(R.id.videoPreview),
                metricsLabel = activity.findViewById(R.id.videoMetricsLabel),
                loraToggle = activity.findViewById(R.id.loraToggle),
                modelPresetGroup = activity.findViewById(R.id.modelPresetGroup),
                shareLogsButton = activity.findViewById(R.id.btnShareLogs),
                logPathLabel = activity.findViewById(R.id.logPathLabel),
            )
    }
}
