# llmedge Minimal Example App

A tiny Android app that uses the llmedge library via a local AAR.

## How it works
- Consumes `llmedge-release.aar` located in `app/libs`.
- Demonstrates blocking and streaming responses in `MainActivity` and a simple RAG flow in `RagActivity`.

## Prerequisites
- Android SDK with NDK r27+, CMake 3.22+
- A GGUF model placed in `app/src/main/assets/YourModel.gguf` (rename as needed)

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

Open the app on device. It first runs a blocking query, then streams a haiku.

## Notes
- The app copies the model once from assets to app-private storage, then loads it from there.
- Tune `numThreads` in `InferenceParams` for your device.
