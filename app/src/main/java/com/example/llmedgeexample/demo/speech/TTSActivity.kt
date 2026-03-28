package com.example.llmedgeexample.demo.speech

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.llmedgeexample.R
import com.example.llmedgeexample.common.*
import io.aatricks.llmedge.speech.tts.BarkTTS
import io.aatricks.llmedge.speech.AudioStreamEvent
import io.aatricks.llmedge.speech.BarkLoadOptions
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
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

    private val statusLabel: TextView by lazy { findViewById(R.id.ttsStatusLabel) }
    private val textInput: EditText by lazy { findViewById(R.id.ttsTextInput) }
    private val logOutput: TextView by lazy { findViewById(R.id.ttsLogOutput) }
    private val logScroll: ScrollView by lazy { findViewById(R.id.ttsLogScroll) }
    private val progressBar: ProgressBar by lazy { findViewById(R.id.ttsProgressBar) }
    private val progressLabel: TextView by lazy { findViewById(R.id.ttsProgressLabel) }
    private val generateButton: Button by lazy { findViewById(R.id.btnGenerate) }
    private val playButton: Button by lazy { findViewById(R.id.btnPlay) }
    private val saveButton: Button by lazy { findViewById(R.id.btnSave) }
    private val downloadButton: Button by lazy { findViewById(R.id.btnDownloadBarkModel) }
    private val timingLabel: TextView by lazy { findViewById(R.id.ttsTiming) }

    private var generationJob: Job? = null
    private var audioTrack: AudioTrack? = null
    private var lastAudioResult: BarkTTS.AudioResult? = null
    private val edge by lazy(LazyThreadSafetyMode.NONE) { bindEdge(this, this, lifecycleScope) }

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
        generateButton.setOnClickListener {
            val text = textInput.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "Please enter some text", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            generateSpeech(text)
        }

        playButton.setOnClickListener {
            lastAudioResult?.let { playAudio(it) }
                ?: Toast.makeText(this, "No audio generated yet", Toast.LENGTH_SHORT).show()
        }

        saveButton.setOnClickListener {
            lastAudioResult?.let { saveAudio(it) }
                ?: Toast.makeText(this, "No audio generated yet", Toast.LENGTH_SHORT).show()
        }

        downloadButton.setOnClickListener { 
            generateSpeech(textInput.text.toString().trim().ifEmpty { "Hello" }) 
        }
    }

    private fun updateUIState() {
        // LLMEdge resolves and caches Bark weights automatically on first use
        statusLabel.text = "Ready - model will download on first use (~800MB)"
        downloadButton.text = "Generate (will download model)"
        downloadButton.visibility = View.VISIBLE
        generateButton.visibility = View.GONE
        playButton.isEnabled = false
        saveButton.isEnabled = false
    }

    private fun log(message: String) {
        runOnUiThread {
            logOutput.append("$message\n")
            logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun generateSpeech(text: String) {
        if (text.isEmpty()) {
            Toast.makeText(this, "Please enter some text", Toast.LENGTH_SHORT).show()
            return
        }

        // Cancel any existing generation
        generationJob?.cancel()
        stopPlayback()

        downloadButton.isEnabled = false
        generateButton.isEnabled = false
        playButton.isEnabled = false
        saveButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        progressBar.isIndeterminate = false
        progressLabel.text = "Starting..."
        statusLabel.text = "Generating speech..."

        log("Generating speech for: \"$text\"")
        log("Note: First run will download ~800MB model from Hugging Face")

        generationJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()

                var result: BarkTTS.AudioResult? = null
                edge.speech.synthesizeStream(
                    text = text,
                    params = BarkTTS.GenerateParams(nThreads = Runtime.getRuntime().availableProcessors()),
                    loadOptions =
                        BarkLoadOptions(
                            seed = 0,
                            temperature = 0.7f,
                            fineTemperature = 0.5f,
                        ),
                ).collect { event ->
                    when (event) {
                        AudioStreamEvent.Started -> Unit
                        is AudioStreamEvent.Progress -> {
                            runOnUiThread {
                                val stepName = when (event.step) {
                                    BarkTTS.EncodingStep.SEMANTIC -> "Semantic"
                                    BarkTTS.EncodingStep.COARSE -> "Coarse"
                                    BarkTTS.EncodingStep.FINE -> "Fine"
                                }
                                progressLabel.text = "$stepName: ${event.percent}%"
                                val base = when (event.step) {
                                    BarkTTS.EncodingStep.SEMANTIC -> 0
                                    BarkTTS.EncodingStep.COARSE -> 33
                                    BarkTTS.EncodingStep.FINE -> 66
                                }
                                progressBar.progress = base + (event.percent / 3)
                            }
                        }
                        is AudioStreamEvent.Result -> result = event.audio
                        AudioStreamEvent.Completed -> Unit
                    }
                }
                val audioResult = requireNotNull(result) { "Speech generation completed without audio output" }

                val genTime = System.currentTimeMillis() - startTime

                withContext(Dispatchers.Main) {
                    lastAudioResult = audioResult
                    progressBar.visibility = View.GONE
                    progressLabel.text = "Complete"
                    
                    // Switch to generate button after first successful run
                    downloadButton.visibility = View.GONE
                    generateButton.visibility = View.VISIBLE
                    generateButton.isEnabled = true
                    
                    playButton.isEnabled = true
                    saveButton.isEnabled = true
                    statusLabel.text = "Model ready"

                    val timing = "Generated ${audioResult.samples.size} samples " +
                        "(${String.format("%.2f", audioResult.durationSeconds)}s) in ${genTime / 1000L}s"
                    timingLabel.text = timing
                    log(timing)
                    log("Real-time factor: ${String.format("%.4f", audioResult.durationSeconds * 1000.0 / genTime)}x")
                }
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    downloadButton.isEnabled = true
                    generateButton.isEnabled = true
                    log("Generation cancelled")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    downloadButton.isEnabled = true
                    generateButton.isEnabled = true
                    statusLabel.text = "Generation failed: ${e.message}"
                    log("Error: ${e.message}")
                    FileLogger.e(TAG, "Generation failed", e)
                }
            }
        }
    }

    private fun playAudio(audio: BarkTTS.AudioResult) {
        stopPlayback()

        log("Playing audio (${String.format("%.2f", audio.durationSeconds)}s)...")

        // Convert float samples to 16-bit PCM
        val pcmData = ShortArray(audio.samples.size)
        for (i in audio.samples.indices) {
            val sample = audio.samples[i].coerceIn(-1.0f, 1.0f)
            pcmData[i] = (sample * 32767).toInt().toShort()
        }

        val bufferSize = AudioTrack.getMinBufferSize(
            audio.sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(audio.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize.coerceAtLeast(pcmData.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack?.write(pcmData, 0, pcmData.size)
        audioTrack?.play()

        playButton.text = "Stop"
        playButton.setOnClickListener {
            stopPlayback()
            playButton.text = "Play"
            playButton.setOnClickListener { lastAudioResult?.let { playAudio(it) } }
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
                
                // Write WAV file directly
                saveAsWav(audio.samples, audio.sampleRate, outputFile.absolutePath)

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

    /** Save audio samples to a WAV file */
    private fun saveAsWav(samples: FloatArray, sampleRate: Int, filePath: String) {
        val file = File(filePath)
        file.parentFile?.mkdirs()

        FileOutputStream(file).use { fos ->
            val wavHeader = createWavHeader(samples.size, sampleRate)
            fos.write(wavHeader)

            // Convert float samples to 16-bit PCM
            val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in samples) {
                val clamped = sample.coerceIn(-1.0f, 1.0f)
                val pcm16 = (clamped * 32767.0f).toInt().toShort()
                buffer.putShort(pcm16)
            }
            fos.write(buffer.array())
        }
    }

    private fun createWavHeader(numSamples: Int, sampleRate: Int): ByteArray {
        val byteRate = sampleRate * 2 // 16-bit mono
        val dataSize = numSamples * 2
        val fileSize = 36 + dataSize

        val buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(fileSize)
        buffer.put("WAVE".toByteArray())

        // fmt subchunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // Subchunk1Size (16 for PCM)
        buffer.putShort(1) // AudioFormat (1 for PCM)
        buffer.putShort(1) // NumChannels (1 for mono)
        buffer.putInt(sampleRate) // SampleRate
        buffer.putInt(byteRate) // ByteRate
        buffer.putShort(2) // BlockAlign (2 for 16-bit mono)
        buffer.putShort(16) // BitsPerSample

        // data subchunk
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)

        return buffer.array()
    }
}
