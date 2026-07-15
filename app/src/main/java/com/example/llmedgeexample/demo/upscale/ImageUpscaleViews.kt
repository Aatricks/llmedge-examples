package com.example.llmedgeexample.demo.upscale

import android.app.Activity
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.example.llmedgeexample.R

internal data class ImageUpscaleViews(
    val pickImageButton: Button,
    val startUpscaleButton: Button,
    val saveImageButton: Button,
    val progressBar: ProgressBar,
    val progressLabel: TextView,
    val previewImage: ImageView
) {
    companion object {
        fun bind(activity: Activity): ImageUpscaleViews =
            ImageUpscaleViews(
                pickImageButton = activity.findViewById(R.id.btnPickImage),
                startUpscaleButton = activity.findViewById(R.id.btnStartUpscale),
                saveImageButton = activity.findViewById(R.id.btnSaveUpscaledImage),
                progressBar = activity.findViewById(R.id.upscaleProgressBar),
                progressLabel = activity.findViewById(R.id.upscaleProgressLabel),
                previewImage = activity.findViewById(R.id.upscalePreview)
            )
    }
}
