package com.example.llmedgeexample.samples

import io.aatricks.llmedge.LLMEdge
import io.aatricks.llmedge.text.TextModelOptions
import io.aatricks.llmedge.tools.BashToolFactory
import io.aatricks.llmedge.tools.BashToolOptions
import io.aatricks.llmedge.tools.Tool
import io.aatricks.llmedge.tools.ToolAgent
import io.aatricks.llmedge.tools.ToolPolicies

/**
 * Example custom-tool wiring for JVM or desktop hosts that provide a bash-compatible shell.
 *
 * Android devices typically do not ship `bash`, so treat this as a host-side example rather than
 * something the example app expects to execute successfully on-device.
 */
object CustomToolSamples {
    fun buildJvmBashTools(): List<Tool> =
        listOf(
            BashToolFactory(
                BashToolOptions(
                    allowRawShell = true,
                    defaultWorkingDirectory = ".",
                ),
            ).createBashTool(),
        )

    fun setupJvmBashAgent(edge: LLMEdge): ToolAgent =
        edge.text.toolAgent(
            tools = buildJvmBashTools(),
            systemPrompt = "Use shell commands only when they materially help answer the user.",
            options = TextModelOptions(useVulkan = false),
            policy = ToolPolicies.ALLOW_ALL,
        )

    suspend fun runJvmBashExample(edge: LLMEdge): String =
        setupJvmBashAgent(edge)
            .reply("Run `pwd` with the bash tool and summarize the result.")
            .text
}
