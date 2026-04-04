package com.example.llmedgeexample.demo.speech

import io.aatricks.llmedge.LLMEdge
import io.aatricks.llmedge.speech.AudioStreamEvent
import io.aatricks.llmedge.speech.BarkLoadOptions
import io.aatricks.llmedge.speech.tts.BarkTTS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class TTSGenerationResult(
    val audio: BarkTTS.AudioResult,
    val generationTimeMs: Long,
)

internal data class TTSGenerationCallbacks(
    val onLog: (String) -> Unit,
    val onStarted: () -> Unit,
    val onProgress: (String, Int) -> Unit,
    val onCompleted: (TTSGenerationResult) -> Unit,
    val onCancelled: () -> Unit,
    val onError: (String) -> Unit,
    val onFinished: () -> Unit,
)

internal class TTSController(
    private val scope: CoroutineScope,
    private val edge: LLMEdge,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    private var generationJob: Job? = null

    fun generate(
        text: String,
        callbacks: TTSGenerationCallbacks,
    ) {
        generationJob?.cancel()
        callbacks.onStarted()
        generationJob =
            scope.launch(ioDispatcher) {
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
                                val stepName =
                                    when (event.step) {
                                        BarkTTS.EncodingStep.SEMANTIC -> "Semantic"
                                        BarkTTS.EncodingStep.COARSE -> "Coarse"
                                        BarkTTS.EncodingStep.FINE -> "Fine"
                                    }
                                val base =
                                    when (event.step) {
                                        BarkTTS.EncodingStep.SEMANTIC -> 0
                                        BarkTTS.EncodingStep.COARSE -> 33
                                        BarkTTS.EncodingStep.FINE -> 66
                                    }
                                withContext(mainDispatcher) {
                                    callbacks.onProgress("$stepName: ${event.percent}%", base + (event.percent / 3))
                                }
                            }
                            is AudioStreamEvent.Result -> result = event.audio
                            AudioStreamEvent.Completed -> Unit
                        }
                    }
                    val audioResult = requireNotNull(result) { "Speech generation completed without audio output" }
                    withContext(mainDispatcher) {
                        callbacks.onCompleted(
                            TTSGenerationResult(
                                audio = audioResult,
                                generationTimeMs = System.currentTimeMillis() - startTime,
                            ),
                        )
                    }
                } catch (_: CancellationException) {
                    withContext(mainDispatcher) {
                        callbacks.onCancelled()
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
        generationJob?.cancel()
    }
}
