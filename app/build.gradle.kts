plugins {
    id("com.android.application") version "8.10.1"
    id("org.jetbrains.kotlin.android") version "2.0.0"
}

// Note: we depend on ML Kit text-recognition at runtime. Avoid referencing
// coordinates that may not be available in the example app's repositories.

android {
    namespace = "com.example.llmedgeexample"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.llmedgeexample"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        val debugKeystore = file("${System.getProperty("user.home")}/.android/debug.keystore")
        if (debugKeystore.exists()) {
            create("debug_key") {
                storeFile = debugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            val debugKeySigning = signingConfigs.findByName("debug_key")
            if (debugKeySigning != null) {
                signingConfig = debugKeySigning
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Use the bundled AAR placed in this app's libs/ directory (avoids AGP artifact extraction issues)
    implementation(files("libs/llmedge-release.aar"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    // Provides TasksKt.await extension used when awaiting Task<T> from ML Kit
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // ML Kit Text Recognition required at runtime when using MlKitOcrEngine from the library AAR
    implementation("com.google.mlkit:text-recognition:16.0.0")
    // ML Kit Image Labeling used by LocalImageDescriber
    implementation("com.google.mlkit:image-labeling:17.0.7")
    // Note: don't request unavailable coordinates here; rely on text-recognition artifact below.

    // Match library deps needed by RAG demo
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.google.code.gson:gson:2.11.0")

     // Required: sentence-embeddings used by the library (file-based AAR does not pull transitives)
    implementation("io.gitlab.shubham0204:sentence-embeddings:v6")

    // Hugging Face client dependencies (needed at runtime when using the library helper)
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-okhttp:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Android instrumented test dependencies
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("org.jetbrains.kotlin:kotlin-test:2.0.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}
