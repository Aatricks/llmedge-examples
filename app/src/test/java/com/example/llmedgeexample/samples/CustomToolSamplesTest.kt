package com.example.llmedgeexample.samples

import io.aatricks.llmedge.tools.ToolKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomToolSamplesTest {
    @Test
    fun `bash tool sample exposes the custom action tool`() {
        val tools = CustomToolSamples.buildJvmBashTools()

        assertEquals(1, tools.size)
        assertEquals("run_bash_command", tools.single().name)
        assertEquals(ToolKind.ACTION, tools.single().kind)
        assertTrue("argv" in tools.single().schema.parameters)
        assertTrue("command" in tools.single().schema.parameters)
        assertTrue("workingDirectory" in tools.single().schema.parameters)
    }
}
