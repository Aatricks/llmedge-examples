# SmolLM Minimal Example App

A tiny Android app that uses the SmolLM library via a local AAR.

## How it works
- Consumes `smollm-release.aar` located in `app/libs`.
- Demonstrates blocking and streaming responses in `MainActivity`.

## Prerequisites
- Android SDK with NDK r27+, CMake 3.22+
- A GGUF model placed in `app/src/main/assets/YourModel.gguf` (rename as needed)

## Build & Run

First build the AAR from the repo root and copy it into the example app:

```powershell
cd ..\..\
.\u200c\gradlew :smollm:assembleRelease
Copy-Item -Force .\smollm\build\outputs\aar\smollm-release.aar .\examples\minimal-app\app\libs\smollm-release.aar
```

Then build and install the example:

```powershell
cd examples\minimal-app
..\..\gradlew :app:assembleDebug
..\..\gradlew :app:installDebug
```

Open the app on device. It first runs a blocking query, then streams a haiku.

## Notes
- The app copies the model once from assets to app-private storage, then loads it from there.
- Tune `numThreads` in `InferenceParams` for your device.
