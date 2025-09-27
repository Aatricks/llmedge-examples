# llmedge Minimal Example App

A tiny Android app that uses the llmedge library via a local AAR.

Find the llmedge library here [main repo](https://github.com/Aatricks/llmedge).

## How it works
- Consumes `llmedge-release.aar` located in `app/libs`.
- `MainActivity` now presents a launcher menu for multiple demos.
	- `LocalAssetDemoActivity` reproduces the original blocking + streaming flow using an on-device asset.
	- `HuggingFaceDemoActivity` downloads a GGUF from the Hugging Face hub and immediately runs it via `SmolLM.loadFromHuggingFace`.
	- `RagActivity` keeps the on-device RAG showcase intact.

## Prerequisites
- Android SDK with NDK r27+, CMake 3.22+
- For the local asset demo only: place a GGUF model at `app/src/main/assets/YourModel.gguf`
	(for example [smolm2-360M](https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF)).
	The Hugging Face demo downloads models on demand, so it does not require a bundled asset.

## Build & Run

First build the AAR from the lib repo root and copy it into the example app:

```powershell
.\gradlew :llmedge:assembleRelease
Copy-Item -Force .\llmedge\build\outputs\aar\llmedge-release.aar .\llmedge-examples\app\libs\llmedge-release.aar
```

Then build and install the example:

```powershell
cd llmedge-examples
..\gradlew :app:assembleDebug
..\gradlew :app:installDebug
```

Open the app on device and pick a demo from the launcher:

- **Local GGUF asset**: copies the bundled model to app storage, runs a blocking prompt, then streams a haiku.
- **Download from Hugging Face**: downloads (or reuses) a model from the hub and runs a quick prompt once loaded.
- **RAG demo**: unchanged from the previous release.

## Notes
- The local asset demo copies the model once from assets to app-private storage, then loads it from there.
- Hugging Face downloads are cached under `filesDir/hf-models/<repo>/<revision>/` for reuse.
- Tune `numThreads` in `InferenceParams` for your device.
