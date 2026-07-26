package com.example.llmedgeexample.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.aatricks.llmedge.speech.tts.BarkTTS
import io.aatricks.llmedge.text.runtime.SmolLM
import io.aatricks.llmedge.image.diffusion.StableDiffusion
import io.aatricks.llmedge.speech.stt.Whisper
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * Smoke test to verify the native library bindings are accessible on device.
 *
 * Run via adb:
 * adb shell am instrument -w -e class com.example.llmedgeexample.LibraryBindingsTest \
 *   com.example.llmedgeexample.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class LibraryBindingsTest {

    private lateinit var context: Context

    companion object {
        private const val TAG = "LibraryBindingsTest"
    }

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    /**
     * Test that SmolLM native bindings are available by creating an instance
     */
    @Test
    fun testSmolLMBindingsAvailable() {
        android.util.Log.i(TAG, "Testing SmolLM native bindings...")
        
        // SmolLM loads its native library in the companion object init block
        // Creating an instance will trigger the load
        val bindingsAvailable = try {
            val smol = SmolLM(useVulkan = true)
            smol.close()
            android.util.Log.i(TAG, "SmolLM instance created successfully")
            true
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e(TAG, "SmolLM native library not found: ${e.message}")
            false
        } catch (e: Exception) {
            // Other exceptions might be OK (e.g. no model loaded)
            android.util.Log.i(TAG, "SmolLM loaded (non-link exception: ${e.message})")
            true
        }
        
        assertTrue(bindingsAvailable, "SmolLM native bindings should be available")
        android.util.Log.i(TAG, "SmolLM bindings: PASSED")
    }

    /**
     * Test that StableDiffusion native bindings are available
     */
    @Test
    fun testStableDiffusionBindingsAvailable() {
        android.util.Log.i(TAG, "Testing StableDiffusion native bindings...")
        
        val bindingsAvailable = try {
            StableDiffusion.checkBindings()
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e(TAG, "StableDiffusion native library not found: ${e.message}")
            false
        } catch (e: Exception) {
            android.util.Log.e(TAG, "StableDiffusion bindings error: ${e.message}")
            false
        }
        
        assertTrue(bindingsAvailable, "StableDiffusion native bindings should be available")
        android.util.Log.i(TAG, "StableDiffusion bindings: PASSED")
    }

    /**
     * Test that Whisper native bindings are available
     */
    @Test
    fun testWhisperBindingsAvailable() {
        android.util.Log.i(TAG, "Testing Whisper native bindings...")
        
        val bindingsAvailable = try {
            // Whisper.checkBindings() verifies the native library is loaded
            Whisper.checkBindings()
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e(TAG, "Whisper native library not found: ${e.message}")
            false
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Whisper bindings error: ${e.message}")
            false
        }
        
        assertTrue(bindingsAvailable, "Whisper native bindings should be available")
        android.util.Log.i(TAG, "Whisper bindings: PASSED")
    }

    /**
     * Test that BarkTTS native bindings are available
     */
    @Test
    fun testBarkTTSBindingsAvailable() {
        android.util.Log.i(TAG, "Testing BarkTTS native bindings...")
        
        val bindingsAvailable = try {
            // BarkTTS.checkBindings() verifies the native library is loaded
            BarkTTS.checkBindings()
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e(TAG, "BarkTTS native library not found: ${e.message}")
            false
        } catch (e: Exception) {
            android.util.Log.e(TAG, "BarkTTS bindings error: ${e.message}")
            false
        }
        
        assertTrue(bindingsAvailable, "BarkTTS native bindings should be available")
        android.util.Log.i(TAG, "BarkTTS bindings: PASSED")
    }

    /**
     * Summary test that all bindings are available
     */
    @Test
    fun testAllBindingsAvailable() {
        android.util.Log.i(TAG, "========================================")
        android.util.Log.i(TAG, "LIBRARY BINDINGS SMOKE TEST")
        android.util.Log.i(TAG, "========================================")
        
        val results = mutableMapOf<String, Boolean>()
        
        // Test SmolLM
        results["SmolLM"] = try {
            val smol = SmolLM(useVulkan = true)
            smol.close()
            true
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e(TAG, "SmolLM: FAILED - ${e.message}")
            false
        } catch (e: Throwable) {
            // Non-link exceptions are OK
            true
        }
        
        // Test StableDiffusion
        results["StableDiffusion"] = try {
            StableDiffusion.checkBindings()
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "StableDiffusion: FAILED - ${e.message}")
            false
        }
        
        // Test Whisper
        results["Whisper"] = try {
            Whisper.checkBindings()
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "Whisper: FAILED - ${e.message}")
            false
        }
        
        // Test BarkTTS
        results["BarkTTS"] = try {
            BarkTTS.checkBindings()
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "BarkTTS: FAILED - ${e.message}")
            false
        }
        
        android.util.Log.i(TAG, "========================================")
        android.util.Log.i(TAG, "RESULTS:")
        results.forEach { (name, passed) ->
            val status = if (passed) "✓ PASSED" else "✗ FAILED"
            android.util.Log.i(TAG, "  $name: $status")
        }
        android.util.Log.i(TAG, "========================================")
        
        val allPassed = results.values.all { it }
        assertTrue(allPassed, "All native library bindings should be available")
    }
}
