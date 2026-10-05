plugins {
    alias(libs.plugins.android.application)
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.offlinemeshchat"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.offlinemeshchat"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }

    // Avoid compression for TensorFlow Lite and model files
    androidResources {
        noCompress += listOf("tflite", "task")
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    // Room database
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // Existing TensorFlow Lite dependencies
    implementation("org.tensorflow:tensorflow-lite:2.16.1") {
        exclude(
            group = "org.tensorflow",
            module = "tensorflow-lite-api"
        )
    }

    implementation("org.tensorflow:tensorflow-lite-support:0.4.4") {
        exclude(
            group = "org.tensorflow",
            module = "tensorflow-lite-support-api"
        )
    }

    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.16.1") {
        exclude(
            group = "org.tensorflow",
            module = "tensorflow-lite-api"
        )
    }

    // Google AI Edge / MediaPipe GenAI
    implementation("com.google.mediapipe:tasks-genai:0.10.27")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}