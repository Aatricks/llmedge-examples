package com.example.llmedgeexample.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.llmedgeexample.R
import com.example.llmedgeexample.common.*
import com.example.llmedgeexample.demo.conversion.SafetensorsConversionActivity
import com.example.llmedgeexample.demo.image.ImageGenerationActivity
import com.example.llmedgeexample.demo.upscale.ImageUpscaleActivity
import com.example.llmedgeexample.demo.rag.RagActivity
import com.example.llmedgeexample.demo.speech.TTSActivity
import com.example.llmedgeexample.demo.speech.TranscriptionActivity
import com.example.llmedgeexample.demo.text.HuggingFaceDemoActivity
import com.example.llmedgeexample.demo.text.JinjaTemplateDemoActivity
import com.example.llmedgeexample.demo.text.LocalAssetDemoActivity
import com.example.llmedgeexample.demo.tools.ToolCallingDemoActivity
import com.example.llmedgeexample.demo.video.VideoGenerationActivity
import com.example.llmedgeexample.demo.vision.ImageToTextActivity
import com.example.llmedgeexample.demo.vision.LlavaVisionActivity
import kotlin.reflect.KClass

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
    private data class DemoDestination(
        val buttonId: Int,
        val activityClass: KClass<out AppCompatActivity>,
    )

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

        demoDestinations().forEach { destination ->
            findViewById<Button>(destination.buttonId).setOnClickListener {
                startActivity(Intent(this, destination.activityClass.java))
            }
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
            memInfoView.text = buildDemoMemorySummary(isCpuOnlyForced(this))
        } catch (e: Exception) {
            // TextView might not exist in layout - that's okay
        }
    }

    private fun demoDestinations(): List<DemoDestination> =
        listOf(
            DemoDestination(R.id.btnOpenLocal, LocalAssetDemoActivity::class),
            DemoDestination(R.id.btnOpenJinjaTemplate, JinjaTemplateDemoActivity::class),
            DemoDestination(R.id.btnOpenHuggingFace, HuggingFaceDemoActivity::class),
            DemoDestination(R.id.btnOpenRag, RagActivity::class),
            DemoDestination(R.id.btnOpenImageToText, ImageToTextActivity::class),
            DemoDestination(R.id.btnOpenLlavaVision, LlavaVisionActivity::class),
            DemoDestination(R.id.btnOpenVideoGeneration, VideoGenerationActivity::class),
            DemoDestination(R.id.btnOpenImageGeneration, ImageGenerationActivity::class),
            DemoDestination(R.id.btnOpenImageUpscale, ImageUpscaleActivity::class),
            DemoDestination(R.id.btnOpenToolCalling, ToolCallingDemoActivity::class),
            DemoDestination(R.id.btnOpenConversion, SafetensorsConversionActivity::class),
            DemoDestination(R.id.btnOpenTranscription, TranscriptionActivity::class),
            DemoDestination(R.id.btnOpenTTS, TTSActivity::class),
        )
}
