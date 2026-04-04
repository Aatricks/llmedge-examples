package com.example.llmedgeexample.demo.speech

import android.content.Context
import android.view.View
import io.aatricks.llmedge.huggingface.HuggingFaceHub
import io.aatricks.llmedge.speech.stt.Whisper
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ModelAvailabilityResult(
    val exists: Boolean,
    val modelFile: File,
)

internal data class TranscriptionCallbacks(
    val onProgress: (Int) -> Unit,
    val onSegment: (String) -> Unit,
    val onCompleted: (List<Whisper.TranscriptionSegment>, String?) -> Unit,
    val onError: (String) -> Unit,
    val onFinished: () -> Unit,
)

internal class TranscriptionController(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    private var transcriptionJob: Job? = null

    fun checkModelAvailability(
        filesDir: File,
        modelFileName: String,
        onResult: (ModelAvailabilityResult) -> Unit,
    ) {
        scope.launch(ioDispatcher) {
            val modelFile = File(filesDir, modelFileName)
            withContext(mainDispatcher) {
                onResult(ModelAvailabilityResult(modelFile.exists(), modelFile))
            }
        }
    }

    fun downloadModel(
        context: Context,
        modelId: String,
        filename: String,
        onStarted: () -> Unit,
        onCompleted: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        onStarted()
        scope.launch(ioDispatcher) {
            try {
                val result =
                    HuggingFaceHub.ensureModelOnDisk(
                        context = context.applicationContext,
                        modelId = modelId,
                        filename = filename,
                    )
                withContext(mainDispatcher) {
                    onCompleted(result.file.absolutePath)
                }
            } catch (e: Exception) {
                withContext(mainDispatcher) {
                    onError(e.message ?: "unknown error")
                }
            }
        }
    }

    fun loadModel(
        modelPath: String,
        onStarted: () -> Unit,
        onCompleted: (Whisper, String, Boolean) -> Unit,
        onError: (String) -> Unit,
    ) {
        onStarted()
        scope.launch(ioDispatcher) {
            try {
                val whisper =
                    Whisper.load(
                        modelPath = modelPath,
                        useGpu = false,
                        flashAttn = true,
                    )
                val modelType = whisper.getModelType()
                val multilingual = whisper.isMultilingual()
                withContext(mainDispatcher) {
                    onCompleted(whisper, modelType, multilingual)
                }
            } catch (e: Exception) {
                withContext(mainDispatcher) {
                    onError(e.message ?: "unknown error")
                }
            }
        }
    }

    fun transcribe(
        whisper: Whisper,
        samples: FloatArray,
        callbacks: TranscriptionCallbacks,
    ) {
        transcriptionJob?.cancel()
        transcriptionJob =
            scope.launch(ioDispatcher) {
                try {
                    whisper.setProgressCallback { progress ->
                        scope.launch(mainDispatcher) { callbacks.onProgress(progress) }
                    }
                    whisper.setSegmentCallback { _, startTime, endTime, text ->
                        val startMs = startTime * 10
                        val endMs = endTime * 10
                        scope.launch(mainDispatcher) {
                            callbacks.onSegment("[$startMs-$endMs] $text\n")
                        }
                    }
                    val segments =
                        whisper.transcribe(
                            samples = samples,
                            params =
                                Whisper.TranscribeParams(
                                    nThreads = Runtime.getRuntime().availableProcessors().coerceAtMost(4),
                                    tokenTimestamps = true,
                                    printProgress = false,
                                ),
                        )
                    val lang = whisper.detectLanguage(samples)
                    withContext(mainDispatcher) {
                        callbacks.onCompleted(segments, lang)
                    }
                } catch (_: CancellationException) {
                    withContext(mainDispatcher) {
                        callbacks.onError("Transcription cancelled")
                    }
                } catch (e: Exception) {
                    withContext(mainDispatcher) {
                        callbacks.onError(e.message ?: "unknown error")
                    }
                } finally {
                    withContext(mainDispatcher) {
                        callbacks.onFinished()
                    }
                }
            }
    }

    fun cancel() {
        transcriptionJob?.cancel()
    }
}
