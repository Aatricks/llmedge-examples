package com.example.llmedgeexample.samples

import android.content.Context
import io.aatricks.llmedge.LLMEdge
import io.aatricks.llmedge.text.TextModelOptions
import io.aatricks.llmedge.tools.DeviceToolFactory
import io.aatricks.llmedge.tools.ToolAgent
import io.aatricks.llmedge.tools.ToolPolicies
import kotlinx.coroutines.runBlocking

/**
 * Example usage of the supported ToolAgent API with real-world device tools.
 */
object ExampleUsage {

    fun setupAgent(
        context: Context,
        edge: LLMEdge,
    ): ToolAgent {
        val factory = DeviceToolFactory(context)
        return edge.text.toolAgent(
            tools = factory.createDefaultTools(),
            options = TextModelOptions(useVulkan = false),
            policy = ToolPolicies.ALLOW_ALL,
        )
    }

    fun runExample(
        context: Context,
        edge: LLMEdge,
    ) = runBlocking {
        val agent = setupAgent(context, edge)
        val response = agent.reply("What's my current battery level?")
        println("Agent Response: ${response.text}")
    }
}
