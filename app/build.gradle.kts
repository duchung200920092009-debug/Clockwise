plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zenpulse.wear"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zenpulse.wear"
        minSdk = 30            // Wear OS 3 (required for Health Services)
        targetSdk = 34
        versionCode = 1
        versionName = "0.1-week1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    // Health Services — real-time sensor access (HR, IBI, accelerometer)
    implementation("androidx.health:health-services-client:1.0.0-beta03")
    // ListenableFuture -> coroutine bridge for Health Services async APIs
    implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")
    implementation("com.google.guava:guava:33.2.1-android")

    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // AndroidX core + lifecycle
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // Compose (BOM keeps versions aligned)
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Wear-specific Compose
    implementation("androidx.wear.compose:compose-material:1.3.1")
    implementation("androidx.wear.compose:compose-foundation:1.3.1")
}
