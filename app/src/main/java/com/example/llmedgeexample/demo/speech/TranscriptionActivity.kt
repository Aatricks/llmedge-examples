package com.example.llmedgeexample.demo.speech

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.llmedgeexample.R
import com.example.llmedgeexample.common.*
import io.aatricks.llmedge.speech.stt.Whisper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity demonstrating speech-to-text transcription using Whisper.
 *
 * Features:
 * - Real-time microphone recording
 * - Live transcription with segment callbacks
 * - Language detection
 * - Subtitle generation (SRT/VTT)
 * - Translation to English
 *
 * Requirements:
 * - Microphone permission
 * - Whisper model file (can be downloaded from Hugging Face)
 */
class TranscriptionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TranscriptionActivity"
        private const val DEFAULT_MODEL_FILE = "ggml-base.bin"
        private const val HUGGING_FACE_MODEL_ID = "ggerganov/whisper.cpp"
    }

    private val views by lazy(LazyThreadSafetyMode.NONE) { TranscriptionViews.bind(this) }
    private val controller by lazy(LazyThreadSafetyMode.NONE) { TranscriptionController(lifecycleScope) }

    private var whisper: Whisper? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var transcriptionJob: Job? = null
    private var recordedSamples = mutableListOf<Float>()
    private var isRecording = false

    private val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    startRecording()
                } else {
                    Toast.makeText(this, "Microphone permission required", Toast.LENGTH_LONG).show()
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transcription)

        setupButtons()
        checkModelAvailability()
    }

    private fun setupButtons() {
        views.recordButton.setOnClickListener {
            if (checkMicrophonePermission()) {
                startRecording()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        views.stopButton.setOnClickListener { stopRecording() }

        views.transcribeButton.setOnClickListener {
            if (recordedSamples.isNotEmpty()) {
                transcribeRecordedAudio()
            } else {
                Toast.makeText(this, "No audio recorded yet", Toast.LENGTH_SHORT).show()
            }
        }

        views.downloadButton.setOnClickListener { downloadModel() }

        // Initial button states
        views.stopButton.isEnabled = false
        views.transcribeButton.isEnabled = false
    }

    private fun checkMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun checkModelAvailability() {
        controller.checkModelAvailability(filesDir, DEFAULT_MODEL_FILE) { result ->
            if (result.exists) {
                views.statusLabel.text = "Model ready: ${result.modelFile.name}"
                views.downloadButton.visibility = View.GONE
                loadModel(result.modelFile.absolutePath)
            } else {
                views.statusLabel.text = "Model not found. Please download."
                views.downloadButton.visibility = View.VISIBLE
                views.recordButton.isEnabled = false
            }
        }
    }

    private fun downloadModel() {
        controller.downloadModel(
            context = applicationContext,
            modelId = HUGGING_FACE_MODEL_ID,
            filename = DEFAULT_MODEL_FILE,
            onStarted = {
                views.downloadButton.isEnabled = false
                views.progressBar.visibility = View.VISIBLE
                views.progressBar.isIndeterminate = true
                views.statusLabel.text = "Downloading model..."
            },
            onCompleted = { modelPath ->
                views.statusLabel.text = "Model downloaded!"
                views.progressBar.visibility = View.GONE
                views.downloadButton.visibility = View.GONE
                loadModel(modelPath)
            },
            onError = { message ->
                android.util.Log.e(TAG, "Failed to download model: $message")
                views.statusLabel.text = "Download failed: $message"
                views.progressBar.visibility = View.GONE
                views.downloadButton.isEnabled = true
            },
        )
    }

    private fun loadModel(modelPath: String) {
        controller.loadModel(
            modelPath = modelPath,
            onStarted = {
                views.progressBar.visibility = View.VISIBLE
                views.progressBar.isIndeterminate = true
                views.statusLabel.text = "Loading model..."
            },
            onCompleted = { loadedWhisper, modelType, multilingual ->
                whisper = loadedWhisper
                views.statusLabel.text = "Model loaded: $modelType (multilingual: $multilingual)"
                views.progressBar.visibility = View.GONE
                views.recordButton.isEnabled = true
            },
            onError = { message ->
                android.util.Log.e(TAG, "Failed to load model: $message")
                views.statusLabel.text = "Failed to load model: $message"
                views.progressBar.visibility = View.GONE
            },
        )
    }

    private fun startRecording() {
        if (isRecording) return

        val sampleRate = Whisper.SAMPLE_RATE
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_FLOAT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

        try {
            audioRecord =
                    AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            sampleRate,
                            channelConfig,
                            audioFormat,
                            bufferSize
                    )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Toast.makeText(this, "Failed to initialize AudioRecord", Toast.LENGTH_LONG).show()
                return
            }

            recordedSamples.clear()
            isRecording = true
            audioRecord?.startRecording()

            views.recordButton.isEnabled = false
            views.stopButton.isEnabled = true
            views.transcribeButton.isEnabled = false
            views.statusLabel.text = "Recording..."

            recordingJob =
                    lifecycleScope.launch(Dispatchers.IO) {
                        val buffer = FloatArray(bufferSize / 4)

                        while (isActive && isRecording) {
                            val read =
                                    audioRecord?.read(
                                            buffer,
                                            0,
                                            buffer.size,
                                            AudioRecord.READ_BLOCKING
                                    )
                                            ?: 0
                            if (read > 0) {
                                synchronized(recordedSamples) {
                                    for (i in 0 until read) {
                                        recordedSamples.add(buffer[i])
                                    }
                                }

                                val durationSeconds =
                                        recordedSamples.size / Whisper.SAMPLE_RATE.toFloat()
                                withContext(Dispatchers.Main) {
                                    views.statusLabel.text =
                                            "Recording: ${String.format("%.1f", durationSeconds)}s"
                                }
                            }
                        }
                    }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Microphone permission denied", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        views.recordButton.isEnabled = true
        views.stopButton.isEnabled = false

        val durationSeconds = recordedSamples.size / Whisper.SAMPLE_RATE.toFloat()
        views.statusLabel.text = "Recorded ${String.format("%.1f", durationSeconds)}s of audio"

        if (recordedSamples.isNotEmpty()) {
            views.transcribeButton.isEnabled = true
        }
    }

    private fun transcribeRecordedAudio() {
        val samples = synchronized(recordedSamples) { recordedSamples.toFloatArray() }

        if (samples.isEmpty()) {
            Toast.makeText(this, "No audio to transcribe", Toast.LENGTH_SHORT).show()
            return
        }

        val whisperInstance = whisper ?: run {
            Toast.makeText(this, "Model not loaded", Toast.LENGTH_SHORT).show()
            return
        }

        views.transcribeButton.isEnabled = false
        views.progressBar.visibility = View.VISIBLE
        views.progressBar.isIndeterminate = false
        views.progressBar.progress = 0
        views.progressBar.max = 100
        views.transcriptionText.text = ""
        views.statusLabel.text = "Transcribing..."

        controller.transcribe(
            whisper = whisperInstance,
            samples = samples,
            callbacks =
                TranscriptionCallbacks(
                    onProgress = { progress -> views.progressBar.progress = progress },
                    onSegment = { chunk ->
                        views.transcriptionText.append(chunk)
                        views.transcriptionScroll.post {
                            views.transcriptionScroll.fullScroll(View.FOCUS_DOWN)
                        }
                    },
                    onCompleted = { segments, langId ->
                        views.progressBar.visibility = View.GONE
                        views.statusLabel.text = "Transcription complete: ${segments.size} segments"
                        if (langId != null) {
                            views.languageLabel.text = "Detected language: $langId"
                            views.languageLabel.visibility = View.VISIBLE
                        }
                        views.transcriptionText.text =
                            segments.joinToString("\n") { segment ->
                                "[${segment.startTimeMs}ms - ${segment.endTimeMs}ms] ${segment.text}"
                            }
                    },
                    onError = { message ->
                        views.statusLabel.text = message
                        views.progressBar.visibility = View.GONE
                    },
                    onFinished = {
                        views.transcribeButton.isEnabled = true
                    },
                ),
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        transcriptionJob?.cancel()
        controller.cancel()
        whisper?.close()
        whisper = null
    }
}
