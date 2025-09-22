plugins {
    id("com.android.application") version "8.10.1"
    id("org.jetbrains.kotlin.android") version "2.0.0"
}

android {
    namespace = "com.example.llmedgeexample"
    compileSdk = 35
    defaultConfig {
    applicationId = "com.example.llmedgeexample"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Use the freshly built AAR from the sibling library module
    implementation(files("../../llmedge/build/outputs/aar/llmedge-release.aar"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // Match library deps needed by RAG demo
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.google.code.gson:gson:2.11.0")

     // Required: sentence-embeddings used by the library (file-based AAR does not pull transitives)
    implementation("io.gitlab.shubham0204:sentence-embeddings:v6")
}
