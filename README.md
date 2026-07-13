# llmedge Examples

Comprehensive demonstration applications for the llmedge Android library, showcasing on-device language model inference, RAG pipelines, image generation, and video synthesis capabilities.

**Main Library Repository**: https://github.com/Aatricks/llmedge

## Overview

This example application provides production-ready demonstrations of llmedge's core features. Each activity is designed to illustrate best practices for model loading, memory management, and efficient on-device inference.

## Included Demonstrations

### Language Model Inference

**Local Asset Demo** (`LocalAssetDemoActivity.kt`)
- Demonstrates loading GGUF models bundled within the APK
- Illustrates asset extraction to app-private storage
- Shows both blocking and streaming inference patterns
- Suitable for offline-first applications

**Jinja Chat Template Demo** (`JinjaTemplateDemoActivity.kt`)
- Demonstrates passing an explicit loop-based Jinja chat template through `SmolLM.InferenceParams.chatTemplate`
- Downloads a GGUF model from Hugging Face through `SmolLM.loadFromHuggingFace(...)`
- Shows the exact template string used for the request so the override path is visible in-app

**Hugging Face Demo** (`HuggingFaceDemoActivity.kt`)
- Automated model download from Hugging Face Hub
- Progress monitoring and cache management
- Demonstrates proper error handling for network operations
- Shows model reuse across application sessions

### Retrieval-Augmented Generation

**RAG Demo** (`RagActivity.kt`)
- Complete on-device RAG pipeline implementation
- Document indexing with ONNX embeddings
- Vector similarity search and context retrieval
- Integration with SmolLM for answer generation
- Demonstrates PDF parsing and text chunking strategies

### Vision and Multimodal Processing

**Image Text Extraction** (`ImageToTextActivity.kt`)
- Google ML Kit OCR integration
- Batch image processing capabilities
- Error handling for unsupported image formats
- Demonstrates preprocessing for vision models

**Vision Model Demo** (`LlavaVisionActivity.kt`)
- Vision-capable language model integration
- Image-to-text description generation
- Multimodal input preparation
- Demonstrates vision model inference patterns

### Generative Media

**Image Generation** (`StableDiffusionActivity.kt`)
- Text-to-image synthesis using Stable Diffusion
- LoRA Support: Toggle switch to apply Detail Tweaker LoRA, automatically downloaded from Hugging Face
- EasyCache: Auto-enabled acceleration for supported DiT models (Flux, SD3, Wan, Qwen Image, Z-Image)
- Memory-aware configuration options
- Progressive generation with cancellation support
- Demonstrates VAE loading and tensor offloading strategies

**Video Generation** (`VideoGenerationActivity.kt`)
- Text-to-video synthesis with selectable Wan 2.1 T2V 1.3B and Wan 2.2 TI2V 5B Q6_K models
- Single-frame (`videoFrames = 1`) image-style output
- Multi-file model loading (main + VAE + T5XXL)
- Device capability detection (12GB+ RAM required)
- Frame-by-frame progress monitoring
- Demonstrates proper resource cleanup

### Speech Processing

**Speech-to-Text (STT)** (`STTActivity.kt`)
- Whisper model download from Hugging Face
- Audio recording and transcription
- Real-time streaming transcription support
- Timestamp and SRT generation

**Text-to-Speech (TTS)** (`TTSActivity.kt`)
- Bark model download from Hugging Face via `LLMEdge`
- Text input for speech synthesis
- Progress tracking during generation
- Audio playback and WAV file saving
- ARM-optimized native inference with OpenMP

## System Requirements

### Minimum Requirements
- Android 11+ (API 30)
- 3GB RAM for basic LLM inference
- 500MB free storage for model caching
- 1GB+ free storage for speech models

### Recommended Configuration
- Android 11+ (API 30) with GPU backends enabled
- 8GB RAM for Stable Diffusion
- 12GB+ RAM for video generation (Wan models)
- 5GB free storage for video model pipeline

### Speech Model Requirements
- **Whisper STT**: 75MB-500MB depending on model size (tiny to small)
- **Bark TTS**: 843MB for f16 models

### Development Environment
- Android SDK with NDK r27+
- CMake 3.22+
- Java 17+
- Gradle 8.0+ (wrapper included)

## Building the Application

### Standard Build Process

From the repository root directory:

1. Build the llmedge library:
```bash
./gradlew :llmedge:assembleRelease
```

2. Build the example application:
```bash
cd llmedge-examples
./gradlew :app:assembleDebug
```

3. Install to device:
```bash
./gradlew :app:installDebug
```

### GPU-Enabled Build

For Android GPU builds with OpenCL-first, Vulkan-fallback runtime selection:

```bash
./gradlew :llmedge:assembleRelease \
  -PllmedgeAndroidOpencl=ON \
  -Pandroid.jniCmakeArgs="-DGGML_VULKAN=ON -DSD_VULKAN=ON"

cd llmedge-examples
./gradlew :app:assembleDebug :app:installDebug
```

**Notes**:
- Experimental OpenCL support is Android-only and currently limited to `arm64-v8a`.
- At runtime, `llmedge` prefers OpenCL first, then Vulkan, then CPU for text, Whisper, and image/video.
- Bark remains CPU-only.

## Asset Configuration

### Bundled GGUF Models

Place small GGUF models in `app/src/main/assets/` for offline-first demos:

```
app/src/main/assets/
              └── models/
                  └── smolm2-360M-instruct.gguf
```

Recommended models for bundling:
- SmolLM2-360M-Instruct (~200MB)
- Qwen2-0.5B-Instruct (~300MB)
- TinyLlama-1.1B (~600MB)

### RAG Embeddings

The RAG demo requires ONNX embedding models:

```
app/src/main/assets/
              └── embeddings/
                  └── all-minilm-l6-v2/
                      ├── model.onnx
                      └── tokenizer.json
```

Download from: `sentence-transformers/all-MiniLM-L6-v2` on Hugging Face

### Runtime Model Cache

Models downloaded via Hugging Face are cached at:
```
<app_private_dir>/files/hf-models/<repo>/<revision>/<filename>
```

Cache persists across app restarts and is reused automatically.

## Usage Examples

### Basic LLM Inference

```kotlin
val edge = LLMEdge.create(context, lifecycleScope)

CoroutineScope(Dispatchers.IO).launch {
    val response = edge.text.generate(
        prompt = "Explain quantum computing concisely.",
        model = ModelSpec.huggingFace(
            repoId = "unsloth/Qwen3-0.6B-GGUF",
            filename = "Qwen3-0.6B-Q4_K_M.gguf",
        ),
    )
    
    withContext(Dispatchers.Main) {
        textView.text = response
    }
}
```

### RAG Pipeline

```kotlin
val edge = LLMEdge.create(context, lifecycleScope)
val rag = edge.rag.createSession()
rag.init()

CoroutineScope(Dispatchers.IO).launch {
    val chunks = rag.indexPdf(pdfUri)
    val answer = rag.ask("What are the main conclusions?")

    withContext(Dispatchers.Main) {
        resultView.text = answer
    }
}
```

### Speech-to-Text (Whisper)

```kotlin
val edge = LLMEdge.create(context, lifecycleScope)

CoroutineScope(Dispatchers.IO).launch {
    // Simple transcription
    val text = edge.speech.transcribeToText(audioSamples)

    // Full transcription with timing
    val segments = edge.speech.transcribe(
        audioSamples = audioSamples,
        params = Whisper.TranscribeParams(language = "en"),
    )

    withContext(Dispatchers.Main) {
        segments.forEach { segment ->
            textView.append("[${segment.startTimeMs}ms] ${segment.text}\n")
        }
    }
}
```

### Real-time Streaming Transcription

For live captioning from a microphone:

```kotlin
class LiveCaptionActivity : AppCompatActivity() {
    private var transcriber: StreamingTranscriptionSession? = null

    fun startLiveCaptions() {
        lifecycleScope.launch(Dispatchers.IO) {
            // Create streaming transcriber with sliding window
            transcriber = LLMEdge.create(this@LiveCaptionActivity, lifecycleScope).speech.createStreamingSession(
                params = Whisper.StreamingParams(
                    stepMs = 3000,      // Process every 3 seconds
                    lengthMs = 10000,   // 10-second windows
                    language = "en",
                    useVad = true       // Skip silent audio
                )
            )

            // Collect transcription results
            transcriber?.events()?.collect { segment ->
                withContext(Dispatchers.Main) {
                    captionTextView.text = segment.text
                }
            }
        }
    }

    // Feed audio from microphone (called by AudioRecord callback)
    fun onAudioData(samples: FloatArray) {
        lifecycleScope.launch(Dispatchers.IO) {
            transcriber?.feedAudio(samples)
        }
    }

    fun stopLiveCaptions() {
        transcriber?.stop()
    }
}
```

### Text-to-Speech (Bark)


```kotlin
val edge = LLMEdge.create(context, lifecycleScope)

CoroutineScope(Dispatchers.IO).launch {
    // Generate speech (model auto-downloads on first use)
    val audio = edge.speech.synthesize("Hello, world!")
    audioPlayer.play(audio.samples, audio.sampleRate)
}
```

### Image Generation

```kotlin
val edge = LLMEdge.create(this, lifecycleScope)

val bitmap = edge.image.generate(
    ImageGenerationRequest(
        prompt = "serene mountain landscape, sunset",
        width = 512,
        height = 512,
        steps = 20
    ),
)

imageView.setImageBitmap(bitmap)
```

### Video Generation

```kotlin
val edge = LLMEdge.create(this, lifecycleScope)

// Automatic memory management and sequential loading
edge.image.generateVideo(
    VideoGenerationRequest(
        prompt = "cat walking through garden",
        videoFrames = 9, // Use 1 for a single image-style output
        width = 512,
        height = 512,
        steps = 20,
        cfgScale = 7.0f,
        flowShift = 3.0f,
        forceSequentialLoad = true // Safe for most devices
    )
).collect { event ->
    Log.d("VideoGen", event.toString())
}
```

## Performance Optimization

### Memory Management

**Monitor Memory Usage**:
```kotlin
val snapshot = MemoryMetrics.snapshot(context)
Log.d("Memory", "Native heap: ${snapshot.nativePssKb / 1024}MB")
```

**Optimization Strategies**:
- Use quantized models (Q4_K_M) for lower memory footprint
- Enable CPU offloading for large models
- Close model instances when not in use
- Process images/video in batches with intermediate cleanup

### Thread Configuration

```kotlin
val edge = LLMEdge.create(
    context = context,
    scope = lifecycleScope,
    config = LLMEdgeConfig(
        text = TextRuntimeConfig(
            promptThreads = Runtime.getRuntime().availableProcessors(),
            contextSize = 2048,
        ),
    ),
)
```

### GPU Backends

Verify Android GPU capability:
```kotlin
val textBackends = LLMEdge.getTextBackendAvailability()
val imageBackends = LLMEdge.getImageBackendAvailability()

Log.i("Performance", "Text backends: $textBackends")
Log.i("Performance", "Image backends: $imageBackends")
```

Check logcat for initialization:
```bash
adb logcat -s SmolLM:* SmolSD:* | grep -Ei "opencl|vulkan|backend"
```

## Troubleshooting

### Model Loading Failures

**Symptoms**: `FileNotFoundException`, `IllegalStateException` during load

**Solutions**:
- Verify model file exists in expected location
- Check available storage space
- Ensure network connectivity for Hugging Face downloads
- Validate model file integrity (not corrupted)

### Out of Memory Errors

**Symptoms**: App crashes with OOM during inference or generation

**Solutions**:
- Use smaller models or quantized variants
- Reduce image/video resolution
- Enable CPU offloading: `offloadToCpu = true`
- Lower context window size
- Close unused model instances

### Slow Inference Performance

**Symptoms**: Generation takes excessive time per token/frame

**Solutions**:
- Use quantized models (Q4_K_M, Q3_K_S)
- Reduce inference steps (15-20 is usually sufficient)
- Enable Android GPU backends on compatible devices
- Adjust thread count to match device cores
- Use smaller resolutions for media generation

### Video Generation Failures

**Symptoms**: Crashes or errors when loading Wan models

**Solutions**:
- Verify device has 12GB+ RAM
- Ensure all three files downloaded (main + VAE + T5XXL)
- Use explicit file paths (not modelId shorthand)
- Check stable-diffusion.cpp logs in logcat
- Verify sufficient storage for 6GB+ model files

### Native Library Issues

**Symptoms**: `UnsatisfiedLinkError`, native crashes

**Solutions**:
- Rebuild AAR and reinstall app
- Verify NDK version matches (r27+)
- Check device ABI compatibility
- Inspect logcat for native stack traces
- Clean build: `./gradlew clean`

### Speech Processing Issues

**Symptoms**: Whisper transcription crashing or producing garbled output

**Solutions**:
- Ensure audio is 16kHz mono PCM float32 format
- Use smaller models (tiny/base) for faster processing
- Check that model file downloaded completely

## Testing Infrastructure

### Speech E2E Testing

Run speech tests via adb:
```bash
adb shell am instrument -w -e class com.example.llmedgeexample.SpeechE2ETest \
  com.example.llmedgeexample.test/androidx.test.runner.AndroidJUnitRunner
```

### Headless E2E Testing

Run automated video generation tests:

```bash
adb shell am start -n com.example.llmedgeexample/.HeadlessVideoTestActivity
```

Monitor test execution:
```bash
adb logcat -s VideoE2E:*
```

Test results are logged to logcat with detailed timing and validation metrics.

## Architecture Notes

### Memory Architecture
- Native models allocated via JNI in native heap
- Dalvik heap used only for Java objects and bitmaps
- Large file downloads use system DownloadManager
- Tensor operations execute in native memory space

### Threading Model
- All model operations run on background threads (Dispatchers.IO)
- UI updates dispatched to Main thread
- Blocking calls avoided on UI thread
- Coroutines used for structured concurrency

### Resource Lifecycle
- Models implement `AutoCloseable` for automatic cleanup
- Native resources freed via `close()` method
- File handles managed with try-with-resources pattern
- Memory mapped files used for large model loading

## License

Apache 2.0 - See LICENSE file for details

## Contributing

Contributions are welcome. Please review the main repository's contributing guidelines before submitting pull requests.
