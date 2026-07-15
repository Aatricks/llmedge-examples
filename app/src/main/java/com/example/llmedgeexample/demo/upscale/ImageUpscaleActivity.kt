package com.example.llmedgeexample.demo.upscale

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.llmedgeexample.R
import com.example.llmedgeexample.common.bindEdge
import com.example.llmedgeexample.common.saveBitmapToGallery
import com.example.llmedgeexample.demo.image.EdgeImageGenerationRuntime
import com.example.llmedgeexample.demo.image.ImageGenerationCallbacks
import com.example.llmedgeexample.demo.image.ImageGenerationController
import io.aatricks.llmedge.LLMEdge
import kotlinx.coroutines.launch

class ImageUpscaleActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ImageUpscaleActivity"
    }

    private val views by lazy(LazyThreadSafetyMode.NONE) { ImageUpscaleViews.bind(this) }

    private val edge by lazy(LazyThreadSafetyMode.NONE) {
        bindEdge(this, this, lifecycleScope, preferPerformanceMode = true)
    }

    private val controller by lazy(LazyThreadSafetyMode.NONE) {
        ImageGenerationController(
            scope = lifecycleScope,
            runtime = EdgeImageGenerationRuntime(edge),
            tag = TAG
        )
    }

    private var sourceBitmap: Bitmap? = null
    private var upscaledBitmap: Bitmap? = null

    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri -> loadSelectedImage(uri) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_upscale)

        views.pickImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            imagePickerLauncher.launch(Intent.createChooser(intent, "Select Image"))
        }

        views.startUpscaleButton.setOnClickListener {
            val bitmap = sourceBitmap ?: return@setOnClickListener
            val downscaled = downscaleIfNeeded(bitmap)
            views.startUpscaleButton.isEnabled = false
            views.pickImageButton.isEnabled = false
            views.saveImageButton.isEnabled = false
            views.progressBar.visibility = View.VISIBLE
            views.progressBar.isIndeterminate = false
            views.progressBar.progress = 0

            controller.startUpscale(
                bitmap = downscaled,
                edge = edge,
                callbacks = ImageGenerationCallbacks(
                    onProgress = { percent, label ->
                        views.progressBar.progress = percent
                        views.progressLabel.text = label
                    },
                    onCompleted = {},
                    onUpscaled = { result ->
                        upscaledBitmap = result
                        views.previewImage.setImageBitmap(result)
                        views.saveImageButton.isEnabled = true
                    },
                    onCancelled = { _, _, _ ->
                        views.progressLabel.text = "Upscale cancelled"
                    },
                    onFinished = {
                        views.progressBar.visibility = View.GONE
                        views.startUpscaleButton.isEnabled = sourceBitmap != null
                        views.pickImageButton.isEnabled = true
                    }
                )
            )
        }

        views.saveImageButton.setOnClickListener {
            val bitmap = upscaledBitmap ?: return@setOnClickListener
            lifecycleScope.launch {
                val uri = saveBitmapToGallery(this@ImageUpscaleActivity, bitmap, "upscaled_${System.currentTimeMillis()}.png")
                if (uri != null) {
                    Toast.makeText(this@ImageUpscaleActivity, "Saved to Gallery: $uri", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@ImageUpscaleActivity, "Failed to save image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadSelectedImage(uri: Uri) {
        try {
            val stream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(stream)
            stream?.close()
            if (bitmap != null) {
                sourceBitmap = bitmap
                upscaledBitmap = null
                views.previewImage.setImageBitmap(bitmap)
                views.startUpscaleButton.isEnabled = true
                views.saveImageButton.isEnabled = false
                views.progressLabel.text = "Image loaded (${bitmap.width}x${bitmap.height})"
            } else {
                Toast.makeText(this, "Failed to decode image", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun downscaleIfNeeded(bitmap: Bitmap, maxDim: Int = 1024): Bitmap {
        if (bitmap.width <= maxDim && bitmap.height <= maxDim) {
            return bitmap
        }
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (bitmap.width > bitmap.height) {
            newWidth = maxDim
            newHeight = (maxDim / ratio).toInt().coerceAtLeast(1)
        } else {
            newHeight = maxDim
            newWidth = (maxDim * ratio).toInt().coerceAtLeast(1)
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
