plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zenpulse.wear"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zenpulse.wear"
        minSdk = 30            // Wear OS 3+; Galaxy Watch 4 and up. Watch 6 = Wear OS 4 (API 34).
        targetSdk = 34
        versionCode = 1
        versionName = "0.1-stage1"
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
    // Samsung Health Sensor SDK (the real-time HR / IBI source).
    // The .aar is NOT on Maven — download it from the Samsung Developer portal and
    // drop it into app/libs/ (see app/libs/README.md). Any *.aar there is picked up.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    // Kotlin coroutines — we bridge the SDK's callback listeners into a Flow.
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
    // Cung cấp Icons.Filled.Favorite dùng trong màn hình HR.
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Wear-specific Compose
    implementation("androidx.wear.compose:compose-material:1.3.1")
    implementation("androidx.wear.compose:compose-foundation:1.3.1")
}
