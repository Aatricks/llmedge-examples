package com.example.llmedgeexample.demo.speech

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.llmedgeexample.R
import com.example.llmedgeexample.common.*
import io.aatricks.llmedge.speech.tts.BarkTTS
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity demonstrating text-to-speech synthesis using Bark via LLMEdge.
 *
 * Features:
 * - Text input for speech synthesis
 * - Progress tracking during generation
 * - Audio playback of generated speech
 * - Save to WAV file
 * - Automatic model download from Hugging Face
 *
 * Note: Bark TTS with f16 models is slow on mobile (~6+ minutes for short phrases).
 * This is expected due to the computational intensity of the model on ARM CPUs.
 */
class TTSActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TTSActivity"
    }

    private val views by lazy(LazyThreadSafetyMode.NONE) { TTSViews.bind(this) }
    private var audioTrack: AudioTrack? = null
    private var lastAudioResult: BarkTTS.AudioResult? = null
    private val edge by lazy(LazyThreadSafetyMode.NONE) { bindEdge(this, this, lifecycleScope) }
    private val controller by lazy(LazyThreadSafetyMode.NONE) { TTSController(lifecycleScope, edge) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tts)

        setupButtons()
        updateUIState()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
    }

    private fun setupButtons() {
        views.generateButton.setOnClickListener {
            val text = views.textInput.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "Please enter some text", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            generateSpeech(text)
        }

        views.playButton.setOnClickListener {
            lastAudioResult?.let { playAudio(it) }
                ?: Toast.makeText(this, "No audio generated yet", Toast.LENGTH_SHORT).show()
        }

        views.saveButton.setOnClickListener {
            lastAudioResult?.let { saveAudio(it) }
                ?: Toast.makeText(this, "No audio generated yet", Toast.LENGTH_SHORT).show()
        }

        views.downloadButton.setOnClickListener {
            generateSpeech(views.textInput.text.toString().trim().ifEmpty { "Hello" })
        }
    }

    private fun updateUIState() {
        // LLMEdge resolves and caches Bark weights automatically on first use
        views.statusLabel.text = "Ready - model will download on first use (~800MB)"
        views.downloadButton.text = "Generate (will download model)"
        views.downloadButton.visibility = View.VISIBLE
        views.generateButton.visibility = View.GONE
        views.playButton.isEnabled = false
        views.saveButton.isEnabled = false
    }

    private fun log(message: String) {
        runOnUiThread {
            views.logOutput.append("$message\n")
            views.logScroll.post { views.logScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun generateSpeech(text: String) {
        if (text.isEmpty()) {
            Toast.makeText(this, "Please enter some text", Toast.LENGTH_SHORT).show()
            return
        }

        stopPlayback()

        log("Generating speech for: \"$text\"")
        log("Note: First run will download ~800MB model from Hugging Face")

        controller.generate(
            text = text,
            callbacks =
                TTSGenerationCallbacks(
                    onLog = ::log,
                    onStarted = {
                        views.downloadButton.isEnabled = false
                        views.generateButton.isEnabled = false
                        views.playButton.isEnabled = false
                        views.saveButton.isEnabled = false
                        views.progressBar.visibility = View.VISIBLE
                        views.progressBar.progress = 0
                        views.progressBar.isIndeterminate = false
                        views.progressLabel.text = "Starting..."
                        views.statusLabel.text = "Generating speech..."
                    },
                    onProgress = { label, progress ->
                        views.progressLabel.text = label
                        views.progressBar.progress = progress
                    },
                    onCompleted = { result ->
                        val audioResult = result.audio
                        lastAudioResult = audioResult
                        views.progressBar.visibility = View.GONE
                        views.progressLabel.text = "Complete"
                        views.downloadButton.visibility = View.GONE
                        views.generateButton.visibility = View.VISIBLE
                        views.generateButton.isEnabled = true
                        views.playButton.isEnabled = true
                        views.saveButton.isEnabled = true
                        views.statusLabel.text = "Model ready"
                        val timing = "Generated ${audioResult.samples.size} samples " +
                            "(${String.format("%.2f", audioResult.durationSeconds)}s) in ${result.generationTimeMs / 1000L}s"
                        views.timingLabel.text = timing
                        log(timing)
                        log("Real-time factor: ${String.format("%.4f", audioResult.durationSeconds * 1000.0 / result.generationTimeMs)}x")
                    },
                    onCancelled = {
                        views.progressBar.visibility = View.GONE
                        views.downloadButton.isEnabled = true
                        views.generateButton.isEnabled = true
                        log("Generation cancelled")
                    },
                    onError = { message ->
                        views.progressBar.visibility = View.GONE
                        views.downloadButton.isEnabled = true
                        views.generateButton.isEnabled = true
                        views.statusLabel.text = "Generation failed: $message"
                        log("Error: $message")
                        FileLogger.e(TAG, "Generation failed: $message")
                    },
                    onFinished = {},
                ),
        )
    }

    private fun playAudio(audio: BarkTTS.AudioResult) {
        stopPlayback()

        log("Playing audio (${String.format("%.2f", audio.durationSeconds)}s)...")

        audioTrack = TTSAudioSupport.buildAudioTrack(audio)
        audioTrack?.play()

        views.playButton.text = "Stop"
        views.playButton.setOnClickListener {
            stopPlayback()
            views.playButton.text = "Play"
            views.playButton.setOnClickListener { lastAudioResult?.let { playAudio(it) } }
        }
    }

    private fun stopPlayback() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    private fun saveAudio(audio: BarkTTS.AudioResult) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val outputFile = File(
                    getExternalFilesDir(null),
                    "bark_output_${System.currentTimeMillis()}.wav"
                )
                
                TTSAudioSupport.saveAsWav(audio.samples, audio.sampleRate, outputFile)

                withContext(Dispatchers.Main) {
                    log("Saved to: ${outputFile.name}")
                    Toast.makeText(
                        this@TTSActivity,
                        "Saved to ${outputFile.name}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    log("Error saving: ${e.message}")
                    Toast.makeText(
                        this@TTSActivity,
                        "Failed to save: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

}
