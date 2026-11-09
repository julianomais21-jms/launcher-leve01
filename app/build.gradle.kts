plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "br.com.litecenter"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.com.litecenter"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "0.2"
        // TV Boxes costumam ser armeabi-v7a (32 bits) ou arm64
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=androidx.media3.common.util.UnstableApi"
    }
}

chaquopy {
    defaultConfig {
        version = "3.11"
        buildPython("python3")
        pip {
            install("requests")
            install("six")
        }
    }
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
}
