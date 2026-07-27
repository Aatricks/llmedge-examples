package com.example.llmedgeexample.demo.rag

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RagInitFailureMessageTest {
    @Test
    fun `an unresolved host reports the one-time downloads instead of the raw error`() {
        val message = ragInitFailureMessage(UnknownHostException("Unable to resolve host \"huggingface.co\""))

        assertTrue(message.startsWith("No network."))
        assertTrue(message.contains("embedding model"))
        assertTrue(message.contains("language model"))
        assertTrue(message.contains("reopen this demo"))
    }

    @Test
    fun `a wrapped connectivity failure is still recognised`() {
        val wrapped = IllegalStateException("model load failed", IOException("io", ConnectException("no route")))

        assertTrue(ragInitFailureMessage(wrapped).startsWith("No network."))
    }

    @Test
    fun `a download timeout counts as a connectivity failure`() {
        assertTrue(ragInitFailureMessage(SocketTimeoutException("timeout")).startsWith("No network."))
    }

    @Test
    fun `unrelated failures keep their original message`() {
        val message = ragInitFailureMessage(IllegalStateException("tokenizer mismatch"))

        assertEquals("LLM load failed: tokenizer mismatch", message)
    }

    @Test
    fun `a cyclic cause chain does not hang the classifier`() {
        val first = IOException("first")
        val second = IOException("second", first)
        first.initCause(second)

        assertEquals("LLM load failed: first", ragInitFailureMessage(first))
    }
}
