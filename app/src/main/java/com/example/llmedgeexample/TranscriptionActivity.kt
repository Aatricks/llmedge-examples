package com.example.llmedgeexample

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.aatricks.llmedge.speech.stt.Whisper
import io.aatricks.llmedge.huggingface.HuggingFaceHub
import java.io.File
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

    private val statusLabel: TextView by lazy { findViewById(R.id.transcriptionStatusLabel) }
    private val transcriptionText: TextView by lazy { findViewById(R.id.transcriptionText) }
    private val transcriptionScroll: ScrollView by lazy { findViewById(R.id.transcriptionScroll) }
    private val progressBar: ProgressBar by lazy { findViewById(R.id.transcriptionProgressBar) }
    private val recordButton: Button by lazy { findViewById(R.id.btnStartRecording) }
    private val stopButton: Button by lazy { findViewById(R.id.btnStopRecording) }
    private val transcribeButton: Button by lazy { findViewById(R.id.btnTranscribe) }
    private val downloadButton: Button by lazy { findViewById(R.id.btnDownloadModel) }
    private val languageLabel: TextView by lazy { findViewById(R.id.detectedLanguageLabel) }

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
        recordButton.setOnClickListener {
            if (checkMicrophonePermission()) {
                startRecording()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        stopButton.setOnClickListener { stopRecording() }

        transcribeButton.setOnClickListener {
            if (recordedSamples.isNotEmpty()) {
                transcribeRecordedAudio()
            } else {
                Toast.makeText(this, "No audio recorded yet", Toast.LENGTH_SHORT).show()
            }
        }

        downloadButton.setOnClickListener { downloadModel() }

        // Initial button states
        stopButton.isEnabled = false
        transcribeButton.isEnabled = false
    }

    private fun checkMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun checkModelAvailability() {
        lifecycleScope.launch(Dispatchers.IO) {
            val modelFile = File(filesDir, DEFAULT_MODEL_FILE)
            val modelExists = modelFile.exists()

            withContext(Dispatchers.Main) {
                if (modelExists) {
                    statusLabel.text = "Model ready: ${modelFile.name}"
                    downloadButton.visibility = View.GONE
                    loadModel(modelFile.absolutePath)
                } else {
                    statusLabel.text = "Model not found. Please download."
                    downloadButton.visibility = View.VISIBLE
                    recordButton.isEnabled = false
                }
            }
        }
    }

    private fun downloadModel() {
        downloadButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        statusLabel.text = "Downloading model..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = HuggingFaceHub.ensureModelOnDisk(
                    context = applicationContext,
                    modelId = HUGGING_FACE_MODEL_ID,
                    filename = DEFAULT_MODEL_FILE
                )
                val modelPath = result.file.absolutePath

                withContext(Dispatchers.Main) {
                    statusLabel.text = "Model downloaded!"
                    progressBar.visibility = View.GONE
                    downloadButton.visibility = View.GONE
                    loadModel(modelPath)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to download model", e)
                withContext(Dispatchers.Main) {
                    statusLabel.text = "Download failed: ${e.message}"
                    progressBar.visibility = View.GONE
                    downloadButton.isEnabled = true
                }
            }
        }
    }

    private fun loadModel(modelPath: String) {
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        statusLabel.text = "Loading model..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                whisper =
                        Whisper.load(
                                modelPath = modelPath,
                                useGpu = false, // Force CPU for predictable behavior on all devices
                                flashAttn = true
                        )

                val modelType = whisper?.getModelType() ?: "unknown"
                val isMultilingual = whisper?.isMultilingual() ?: false

                withContext(Dispatchers.Main) {
                    statusLabel.text = "Model loaded: $modelType (multilingual: $isMultilingual)"
                    progressBar.visibility = View.GONE
                    recordButton.isEnabled = true
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to load model", e)
                withContext(Dispatchers.Main) {
                    statusLabel.text = "Failed to load model: ${e.message}"
                    progressBar.visibility = View.GONE
                }
            }
        }
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

            recordButton.isEnabled = false
            stopButton.isEnabled = true
            transcribeButton.isEnabled = false
            statusLabel.text = "Recording..."

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
                                    statusLabel.text =
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

        recordButton.isEnabled = true
        stopButton.isEnabled = false

        val durationSeconds = recordedSamples.size / Whisper.SAMPLE_RATE.toFloat()
        statusLabel.text = "Recorded ${String.format("%.1f", durationSeconds)}s of audio"

        if (recordedSamples.isNotEmpty()) {
            transcribeButton.isEnabled = true
        }
    }

    private fun transcribeRecordedAudio() {
        val samples = synchronized(recordedSamples) { recordedSamples.toFloatArray() }

        if (samples.isEmpty()) {
            Toast.makeText(this, "No audio to transcribe", Toast.LENGTH_SHORT).show()
            return
        }

        transcribeButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = false
        progressBar.progress = 0
        progressBar.max = 100
        transcriptionText.text = ""
        statusLabel.text = "Transcribing..."

        transcriptionJob =
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val whisperInstance =
                                whisper ?: throw IllegalStateException("Model not loaded")

                        // Set up callbacks for real-time updates
                        whisperInstance.setProgressCallback { progress ->
                            lifecycleScope.launch(Dispatchers.Main) {
                                progressBar.progress = progress
                            }
                        }

                        whisperInstance.setSegmentCallback { index, startTime, endTime, text ->
                            lifecycleScope.launch(Dispatchers.Main) {
                                val startMs = startTime * 10
                                val endMs = endTime * 10
                                transcriptionText.append("[$startMs-$endMs] $text\n")
                                transcriptionScroll.post {
                                    transcriptionScroll.fullScroll(View.FOCUS_DOWN)
                                }
                            }
                        }

                        // Perform transcription
                        val segments =
                                whisperInstance.transcribe(
                                        samples = samples,
                                        params =
                                                Whisper.TranscribeParams(
                                                        nThreads =
                                                                Runtime.getRuntime()
                                                                        .availableProcessors()
                                                                        .coerceAtMost(4),
                                                        tokenTimestamps = true,
                                                        printProgress = false
                                                )
                                )

                        // Detect language
                        val langId = whisperInstance.detectLanguage(samples)

                        withContext(Dispatchers.Main) {
                            progressBar.visibility = View.GONE
                            statusLabel.text = "Transcription complete: ${segments.size} segments"

                            if (langId != null) {
                                languageLabel.text = "Detected language: $langId"
                                languageLabel.visibility = View.VISIBLE
                            }

                            // Show full transcription
                            transcriptionText.text =
                                    segments.joinToString("\n") { segment ->
                                        "[${segment.startTimeMs}ms - ${segment.endTimeMs}ms] ${segment.text}"
                                    }

                            transcribeButton.isEnabled = true
                        }
                    } catch (cancelled: CancellationException) {
                        withContext(Dispatchers.Main) {
                            statusLabel.text = "Transcription cancelled"
                            progressBar.visibility = View.GONE
                            transcribeButton.isEnabled = true
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Transcription failed", e)
                        withContext(Dispatchers.Main) {
                            statusLabel.text = "Transcription failed: ${e.message}"
                            progressBar.visibility = View.GONE
                            transcribeButton.isEnabled = true
                        }
                    }
                }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        transcriptionJob?.cancel()
        whisper?.close()
        whisper = null
    }
}
