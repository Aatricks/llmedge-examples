package com.example.llmedgeexample.demo.text

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.llmedgeexample.common.bindEdge
import io.aatricks.llmedge.LLMEdge
import io.aatricks.llmedge.model.ModelSpec
import io.aatricks.llmedge.text.TextModelOptions
import io.aatricks.llmedge.text.runtime.SmolLM
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

internal object EdgeFacadeJavaCompat {
    private val bgDispatcher: CoroutineDispatcher =
        Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "LLMEdge-JavaCompat").apply { isDaemon = true }
        }.asCoroutineDispatcher()

    interface PrepareCallback {
        fun onSuccess(modelPath: String)
        fun onError(t: Throwable)
    }

    interface GenerateCallback {
        fun onSuccess(result: TextGenerationResult)
        fun onError(t: Throwable)
    }

    data class TextGenerationResult(
        val modelPath: String,
        val response: String,
        val tokenCount: Long?,
        val tokensPerSecond: Float?,
    )

    @JvmStatic
    fun createBoundEdge(activity: AppCompatActivity): LLMEdge =
        bindEdge(activity, activity, activity.lifecycleScope)

    @JvmStatic
    fun prepareLocalTextModelAsync(
        edge: LLMEdge,
        modelPath: String,
        callback: PrepareCallback,
    ) {
        runAsync(callback::onError) {
            edge.text.prepare(model = ModelSpec.localFile(modelPath))
            callback.onSuccess(modelPath)
        }
    }

    @JvmStatic
    fun generateAsync(
        edge: LLMEdge,
        modelPath: String,
        prompt: String,
        callback: GenerateCallback,
    ) {
        runAsync(callback::onError) {
            val model = ModelSpec.localFile(modelPath)
            val response = edge.text.generate(prompt = prompt, model = model)
            val metrics = edge.text.getLastGenerationMetrics()
            callback.onSuccess(
                TextGenerationResult(
                    modelPath = modelPath,
                    response = response,
                    tokenCount = metrics?.tokenCount,
                    tokensPerSecond = metrics?.tokensPerSecond,
                ),
            )
        }
    }

    @JvmStatic
    fun generateWithThinkingDisabledAsync(
        edge: LLMEdge,
        modelPath: String,
        prompt: String,
        callback: GenerateCallback,
    ) {
        runAsync(callback::onError) {
            val model = ModelSpec.localFile(modelPath)
            val response =
                edge.text.generate(
                    prompt = prompt,
                    model = model,
                    options =
                        TextModelOptions(
                            thinkingMode = SmolLM.ThinkingMode.DISABLED,
                            reasoningBudget = 0,
                        ),
                )
            val metrics = edge.text.getLastGenerationMetrics()
            callback.onSuccess(
                TextGenerationResult(
                    modelPath = modelPath,
                    response = response,
                    tokenCount = metrics?.tokenCount,
                    tokensPerSecond = metrics?.tokensPerSecond,
                ),
            )
        }
    }

    private fun runAsync(
        onError: (Throwable) -> Unit,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        CoroutineScope(bgDispatcher).launch {
            try {
                block()
            } catch (t: Throwable) {
                onError(t)
            }
        }
    }
}
