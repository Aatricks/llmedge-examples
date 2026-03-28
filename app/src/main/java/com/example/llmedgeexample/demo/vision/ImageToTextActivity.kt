package com.example.llmedgeexample.demo.vision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.llmedgeexample.R
import com.example.llmedgeexample.common.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import io.aatricks.llmedge.vision.ImageUtils

class ImageToTextActivity : AppCompatActivity() {
    private val btnTake: Button by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.btnTakePicture) }
    private val ivPreview: ImageView by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.ivPreview) }
    private val tvResult: TextView by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.tvTextResult) }
    private val progress: ProgressBar by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.progress) }

    private val TAG = "ImageToTextActivity"
    private val edge by lazy(LazyThreadSafetyMode.NONE) { bindEdge(this, this, lifecycleScope) }

    private var photoUri: Uri? = null
    private var photoFile: File? = null

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
        Log.d(TAG, "takePicture callback: success=$success, uri=$photoUri")
        if (success && photoUri != null) {
            try {
                // Load the full-resolution image from the temp file we created
                val file = photoFile
                if (file == null) {
                    Log.e(TAG, "photoFile is null despite success")
                    tvResult.text = "Internal error: missing photo file"
                    return@registerForActivityResult
                }
                val bitmap = ImageUtils.fileToBitmap(file)
                ivPreview.setImageBitmap(bitmap)
                runOcr(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling captured high-res image", e)
                tvResult.text = "Error handling captured image: ${e.message}"
            }
        } else {
            Log.w(TAG, "TakePicture failed or no uri returned")
            tvResult.text = "No image captured"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        setContentView(R.layout.activity_image_to_text)

        // Views are initialized lazily via delegates

        btnTake.setOnClickListener {
            // Request camera permission if needed
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                try {
                    val f = createTempImageFile()
                    photoFile = f
                    photoUri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", f)
                    takePicture.launch(photoUri)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create temp file for camera", e)
                    tvResult.text = "Failed to prepare camera: ${e.message}"
                }
            } else {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 99)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermissionsResult: request=$requestCode, results=${grantResults.contentToString()}")
        if (requestCode == 99 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            try {
                val f = createTempImageFile()
                photoFile = f
                photoUri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", f)
                takePicture.launch(photoUri)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create temp file for camera (permissions)", e)
                tvResult.text = "Failed to prepare camera: ${e.message}"
            }
        } else {
            tvResult.text = "Camera permission denied"
        }
    }

    /**
     * Create a temp image file in external cache directory for full resolution camera capture.
     */
    @Throws(IOException::class)
    private fun createTempImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "JPEG_${timeStamp}_"
        val storageDir: File? = externalCacheDir
        return File.createTempFile(fileName, ".jpg", storageDir)
    }

    private fun runOcr(bitmap: Bitmap) {
        Log.d(TAG, "runOcr: starting OCR")
        progress.visibility = View.VISIBLE
        tvResult.text = ""

        // Attach a CoroutineExceptionHandler so any uncaught coroutine exceptions are logged
        val handler = CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Uncaught coroutine exception", throwable)
            runOnUiThread {
                tvResult.text = "Internal error: ${throwable.message}"
            }
        }

        lifecycleScope.launch(Dispatchers.Main + handler) {
            try {
                val start = System.currentTimeMillis()
                val text = edge.vision.extractText(bitmap)
                val dur = System.currentTimeMillis() - start
                Log.d(TAG, "OCR completed in ${dur}ms, textLength=${text.length}")
                tvResult.text = text.ifEmpty { "(no text detected)" }
            } catch (e: Exception) {
                Log.e(TAG, "OCR failed", e)
                tvResult.text = "OCR failed: ${e.message}"
            } finally {
                progress.visibility = View.GONE
            }
        }
    }
}
