package com.example.llmedgeexample.demo.speech

import io.aatricks.llmedge.LLMEdge
import io.aatricks.llmedge.model.ModelArtifactKind
import io.aatricks.llmedge.model.ModelCapability
import io.aatricks.llmedge.model.ModelHints
import io.aatricks.llmedge.model.ModelSpec
import io.aatricks.llmedge.speech.WhisperLoadOptions
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
        edge: LLMEdge,
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
                    edge.models.prefetch(
                        whisperModelSpec(
                            repoId = modelId,
                            filename = filename,
                        ),
                    )
                withContext(mainDispatcher) {
                    onCompleted(result.absolutePath)
                }
            } catch (e: Exception) {
                withContext(mainDispatcher) {
                    onError(e.message ?: "unknown error")
                }
            }
        }
    }

    fun prepareModel(
        edge: LLMEdge,
        model: ModelSpec,
        onStarted: () -> Unit,
        onCompleted: () -> Unit,
        onError: (String) -> Unit,
    ) {
        onStarted()
        scope.launch(ioDispatcher) {
            try {
                edge.speech.prepareSpeechToText(
                    model = model,
                    loadOptions = WhisperLoadOptions(useGpu = false),
                )
                withContext(mainDispatcher) {
                    onCompleted()
                }
            } catch (e: Exception) {
                withContext(mainDispatcher) {
                    onError(e.message ?: "unknown error")
                }
            }
        }
    }

    fun transcribe(
        edge: LLMEdge,
        model: ModelSpec,
        samples: FloatArray,
        callbacks: TranscriptionCallbacks,
    ) {
        transcriptionJob?.cancel()
        transcriptionJob =
            scope.launch(ioDispatcher) {
                try {
                    withContext(mainDispatcher) { callbacks.onProgress(5) }
                    val segments =
                        edge.speech.transcribe(
                            audioSamples = samples,
                            model = model,
                            params =
                                Whisper.TranscribeParams(
                                    nThreads = Runtime.getRuntime().availableProcessors().coerceAtMost(4),
                                    tokenTimestamps = true,
                                    printProgress = false,
                                ),
                            loadOptions = WhisperLoadOptions(useGpu = false),
                        )
                    withContext(mainDispatcher) {
                        segments.forEach { segment ->
                            callbacks.onSegment("[${segment.startTimeMs}-${segment.endTimeMs}] ${segment.text}\n")
                        }
                        callbacks.onProgress(95)
                    }
                    val lang =
                        edge.speech.detectLanguage(
                            audioSamples = samples,
                            model = model,
                            loadOptions = WhisperLoadOptions(useGpu = false),
                        )
                    withContext(mainDispatcher) {
                        callbacks.onProgress(100)
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

    private fun whisperModelSpec(
        repoId: String,
        filename: String,
    ): ModelSpec =
        ModelSpec.huggingFace(
            repoId = repoId,
            filename = filename,
            preferredQuantizations = emptyList(),
            hints =
                ModelHints(
                    artifactKind = ModelArtifactKind.REPO_FILE,
                    capabilities = setOf(ModelCapability.SPEECH_TO_TEXT),
                ),
        )
}
