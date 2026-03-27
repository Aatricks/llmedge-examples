package com.example.llmedgeexample

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
/**
 * Main activity for LLMEdge Example app.
 *
 * Provides navigation to various demo activities:
 * - Local asset model loading
 * - HuggingFace model download
 * - RAG (Retrieval Augmented Generation)
 * - Image-to-text (OCR)
 * - LLaVA vision analysis
 * - Video generation
 * - Image generation
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val BYTES_IN_MB = 1024L * 1024L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Navigation buttons
        val forceCpuOnlySwitch = findViewById<Switch>(R.id.switchForceCpuOnly)
        forceCpuOnlySwitch.isChecked = isCpuOnlyForced(this)
        forceCpuOnlySwitch.setOnCheckedChangeListener { _, isChecked ->
            setCpuOnlyForced(this, isChecked)
            updateMemoryInfo()
        }

        findViewById<Button>(R.id.btnOpenLocal).setOnClickListener {
            startActivity(Intent(this, LocalAssetDemoActivity::class.java))
        }

        findViewById<Button>(R.id.btnOpenJinjaTemplate).setOnClickListener {
            startActivity(Intent(this, JinjaTemplateDemoActivity::class.java))
        }

        findViewById<Button>(R.id.btnOpenHuggingFace).setOnClickListener {
            startActivity(Intent(this, HuggingFaceDemoActivity::class.java))
        }

        findViewById<Button>(R.id.btnOpenRag).setOnClickListener {
            startActivity(Intent(this, RagActivity::class.java))
        }

        findViewById<Button>(R.id.btnOpenImageToText).setOnClickListener {
            startActivity(Intent(this, ImageToTextActivity::class.java))
        }

        findViewById<Button>(R.id.btnOpenLlavaVision).setOnClickListener {
            startActivity(Intent(this, LlavaVisionActivity::class.java))
        }

        findViewById<Button>(R.id.btnOpenVideoGeneration).setOnClickListener {
            startActivity(Intent(this, VideoGenerationActivity::class.java))
        }

        findViewById<Button>(R.id.btnOpenImageGeneration).setOnClickListener {
            startActivity(Intent(this, ImageGenerationActivity::class.java))
        }

        findViewById<Button>(R.id.btnOpenToolCalling).setOnClickListener {
            startActivity(Intent(this, ToolCallingDemoActivity::class.java))
        }

        findViewById<Button>(R.id.btnOpenTranscription).setOnClickListener {
            startActivity(Intent(this, TranscriptionActivity::class.java))
        }

        findViewById<Button>(R.id.btnOpenTTS).setOnClickListener {
            startActivity(Intent(this, TTSActivity::class.java))
        }

        // Display memory and device info
        updateMemoryInfo()
    }

    override fun onResume() {
        super.onResume()
        updateMemoryInfo()
    }

    private fun updateMemoryInfo() {
        try {
            val memInfoView = findViewById<TextView>(R.id.txtMemoryInfo) ?: return

            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)

            val availMB = memInfo.availMem / BYTES_IN_MB
            val totalMB = memInfo.totalMem / BYTES_IN_MB
            val usedMB = totalMB - availMB

            val runtime = Runtime.getRuntime()
            val heapUsed = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_IN_MB
            val heapMax = runtime.maxMemory() / BYTES_IN_MB

            val sb = StringBuilder()
            sb.appendLine("System: ${usedMB}MB / ${totalMB}MB (${availMB}MB free)")
            sb.appendLine("Heap: ${heapUsed}MB / ${heapMax}MB")

            val gpuStatus = detectGpuBackendStatus()
            sb.appendLine("GPU backends: ${gpuStatus.summary()}")
            sb.appendLine("CPU-only override: ${if (isCpuOnlyForced(this)) "ON" else "OFF"}")
            gpuStatus.vulkanInfo?.let { vulkanInfo ->
                sb.appendLine("Vulkan mem: ${vulkanInfo.freeMemoryMB}MB / ${vulkanInfo.totalMemoryMB}MB")
            }

            // Low memory warning
            val totalRamGB = totalMB / 1024
            if (totalRamGB < 8) {
                sb.appendLine("Note: Low RAM device - sequential loading enabled")
            }

            memInfoView.text = sb.toString().trim()
        } catch (e: Exception) {
            // TextView might not exist in layout - that's okay
        }
    }
}
