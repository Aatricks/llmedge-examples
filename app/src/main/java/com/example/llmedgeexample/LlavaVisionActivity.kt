package com.example.llmedgeexample

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toFile
import java.io.File
import io.aatricks.llmedge.vision.ImageUtils
import io.aatricks.llmedge.vision.LocalImageDescriber
import io.aatricks.llmedge.vision.ImageSource
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LlavaVisionActivity : AppCompatActivity() {
    private val TAG = "LlavaVisionActivity"

    // Use IO dispatcher for native JNI operations instead of MainScope which uses Main dispatcher
    // This provides better parallelism for blocking native calls
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val edge by lazy(LazyThreadSafetyMode.NONE) { bindEdge(this, this, scope, preferPerformanceMode = false) }

    private val btnPick: Button by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.btnPickImage) }
    private val btnTake: Button by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.btnTakePicture) }
    private val btnRun: Button by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.btnRun) }
    private val btnDescribeLocal: Button by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.btnDescribeLocal) }
    private val etPrompt: EditText by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.etPrompt) }
    private val tvResult: TextView by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.tvResult) }
    private val imagePreview: ImageView by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.imagePreview) }
    private val progress: ProgressBar by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.progress) }

    private var imageUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            scope.launch {
                try {
                    val bmp = ImageUtils.imageToBitmap(this@LlavaVisionActivity, ImageSource.UriSource(uri))
                    val displayBmp = ImageUtils.preprocessBitmap(bmp, maxDimension = 1024, enhance = false)
                    runOnUiThread {
                        imagePreview.setImageBitmap(displayBmp)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load image for preview: ${e.message}")
                }
            }
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val safeBmp = ImageUtils.preprocessBitmap(bitmap, maxDimension = 1600, enhance = false)
            imagePreview.setImageBitmap(safeBmp)
            val file = File.createTempFile("llava_input", ".jpg", cacheDir)
            try {
                file.outputStream().use { out ->
                    safeBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                }
                imageUri = Uri.fromFile(file)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save captured image: ${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_llava_vision)

        // Views are initialized lazily via delegates

        btnPick.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnTake.setOnClickListener {
            takePictureLauncher.launch(null)
        }

        btnRun.setOnClickListener {
            runVisionQuery()
        }

        btnDescribeLocal.setOnClickListener {
            runLocalDescribe()
        }
    }

    private fun runLocalDescribe() {
        val uri = imageUri
        if (uri == null) {
            tvResult.text = "Pick or take an image first"
            return
        }

        progress.visibility = View.VISIBLE
        tvResult.text = ""

        val exceptionHandler = CoroutineExceptionHandler { _, ex ->
            Log.e(TAG, "Unhandled coroutine error", ex)
            runOnUiThread {
                progress.visibility = View.GONE
                tvResult.text = "Error: ${ex.message}"
            }
        }

        scope.launch(exceptionHandler) {
            try {
                val localInput = File.createTempFile("llava_input", ".jpg", cacheDir)
                try {
                    val bmp = ImageUtils.imageToBitmap(this@LlavaVisionActivity, ImageSource.UriSource(uri))
                    val scaled = ImageUtils.preprocessBitmap(bmp, maxDimension = 1600, enhance = false)
                    localInput.outputStream().use { out -> scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out) }
                } catch (e: Exception) {
                    contentResolver.openInputStream(uri)?.use { ins ->
                        localInput.outputStream().use { out -> ins.copyTo(out) }
                    }
                }

                // OCR using the vision client
                val bmpForOcr = BitmapFactory.decodeFile(localInput.absolutePath)
                val ocrText = try {
                    edge.vision.extractText(bmpForOcr)
                } catch (e: Exception) {
                    Log.w(TAG, "OCR failed in local describe", e)
                    ""
                }

                // Local description
                val desc = LocalImageDescriber.describe(
                    this@LlavaVisionActivity,
                    ImageSource.FileSource(localInput)
                )

                runOnUiThread {
                    progress.visibility = View.GONE
                    val sb = StringBuilder()
                    sb.appendLine("Local description: ${desc.summary}")
                    if (desc.labels.isNotEmpty()) sb.appendLine("Labels: ${desc.labels.joinToString(", ")}")
                    val size = desc.size
                    if (size != null) sb.appendLine("Size: ${size.first}x${size.second}")
                    if (desc.dominantColor != null) sb.appendLine("Dominant color: ${desc.dominantColor}")
                    if (ocrText.isNotBlank()) {
                        val ocrSnippet = ocrText.take(500)
                        sb.appendLine("OCR: $ocrSnippet")
                    }
                    tvResult.text = sb.toString().trim()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Local describe failed", e)
                runOnUiThread {
                    progress.visibility = View.GONE
                    tvResult.text = "Error: ${e.message}"
                }
            }
        }
    }

    private fun runVisionQuery() {
        val promptText = etPrompt.text.toString().ifBlank { "Describe the image" }
        val uri = imageUri
        if (uri == null) {
            tvResult.text = "Pick or take an image first"
            return
        }

        progress.visibility = View.VISIBLE
        tvResult.text = ""

        val exceptionHandler = CoroutineExceptionHandler { _, ex ->
            Log.e(TAG, "Unhandled coroutine error", ex)
            runOnUiThread {
                progress.visibility = View.GONE
                tvResult.text = "Error: ${ex.message}"
            }
        }

        scope.launch(exceptionHandler) {
            try {
                runOnUiThread { tvResult.text = "Preparing image..." }
                
                // Load bitmap
                val bmp = ImageUtils.imageToBitmap(this@LlavaVisionActivity, ImageSource.UriSource(uri))
                val scaledBmp = ImageUtils.preprocessBitmap(bmp, maxDimension = 1024, enhance = false)

                // 1. Run OCR
                runOnUiThread { tvResult.text = "Running OCR..." }
                val ocrText = try {
                    edge.vision.extractText(scaledBmp)
                } catch (e: Exception) {
                    Log.w(TAG, "OCR failed", e)
                    ""
                }

                // 2. Compute metadata (simplified)
                val width = scaledBmp.width
                val height = scaledBmp.height
                val dimsText = "${width}x${height}"
                
                // 3. Build a plain prompt and let the library/model template own the chat formatting.
                val augmentedPrompt = buildString {
                    appendLine("Describe the image using the prepared visual context.")
                    appendLine("Return compact JSON with keys: objects, attributes, text.")
                    appendLine("Image size: $dimsText")
                    if (ocrText.isNotBlank()) {
                        appendLine("OCR text: ${ocrText.take(1000)}")
                    }
                    appendLine()
                    appendLine("User request: $promptText")
                }

                // 4. Run Vision Analysis
                runOnUiThread { tvResult.text = "Running vision analysis (loading model)..." }
                
                val resultText = edge.vision.analyze(scaledBmp, augmentedPrompt)

                runOnUiThread {
                    progress.visibility = View.GONE
                    // Parse JSON output
                    val pretty = try {
                        val obj = org.json.JSONObject(resultText.trim())
                        val objects = if (obj.has("objects")) {
                            val a = obj.getJSONArray("objects")
                            val list = mutableListOf<String>()
                            for (i in 0 until a.length()) list.add(a.optString(i))
                            if (list.isEmpty()) "No obvious objects detected." else "Objects: ${list.joinToString(", ")}."
                        } else ""
                        val attrs = if (obj.has("attributes")) {
                            val s = obj.optString("attributes")
                            if (s.isNullOrBlank()) "" else "Attributes: $s."
                        } else ""
                        val textField = if (obj.has("text")) {
                            val t = obj.optString("text")
                            if (t.isNullOrBlank()) "" else "Text (OCR): $t"
                        } else ""
                        val parts = listOf(objects, attrs, textField).filter { it.isNotBlank() }
                        if (parts.isEmpty()) "Model response:\n$resultText" else "Model response:\n" + parts.joinToString("\n")
                    } catch (e: Exception) {
                        "Model response:\n$resultText"
                    }
                    tvResult.text = pretty
                }

            } catch (e: Exception) {
                Log.e(TAG, "Vision demo failed", e)
                runOnUiThread {
                    progress.visibility = View.GONE
                    tvResult.text = "Error: ${e.message}"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
