package com.example.llmedgeexample

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import io.aatricks.llmedge.LLMEdge
import io.aatricks.llmedge.LLMEdgeConfig
import io.aatricks.llmedge.lifecycle.LLMEdgeLifecycle
import kotlinx.coroutines.CoroutineScope

fun bindEdge(
    owner: LifecycleOwner,
    context: Context,
    scope: CoroutineScope,
    preferPerformanceMode: Boolean = false,
): LLMEdge =
    LLMEdgeLifecycle.bind(
        owner,
        LLMEdge.create(
            context = context,
            scope = scope,
            config = LLMEdgeConfig(preferPerformanceMode = preferPerformanceMode),
        ),
    )
