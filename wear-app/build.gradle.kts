plugins {
    alias(libs.plugins.android.application)
    kotlin("android")
    kotlin("plugin.compose")
}

android {
    namespace = "com.heartrate.wear"
    compileSdk = 36

    defaultConfig {
        // Wear Data Layer routes messages by app package identity across nodes.
        // Keep watch and phone applicationId identical so listener service can receive payloads.
        applicationId = "com.heartrate.phone"
        minSdk = 34
        // Wear emulator on API 34 crashes in Compose/Wear runtime when targetSdk > 34
        // because reduce_motion setting becomes privileged-only. Keep scaffold app stable.
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
        kotlinCompilerExtensionVersion = "1.5.6"
    }

    buildToolsVersion = "36.0.0"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation(project(":shared"))

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Wear Compose
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.navigation)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")

    // Koin
    implementation(libs.koin.android)

    // Wear Data Layer
    implementation(libs.google.play.services.wearable)

    // Wear Health Services (official API path for resilient workout/background metrics)
    implementation("androidx.health:health-services-client:1.1.0-rc01")
}
