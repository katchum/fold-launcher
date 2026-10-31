plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.xsun.foldlauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xsun.foldlauncher"
        minSdk = 29
        targetSdk = 35
        versionCode = 10
        versionName = "0.10.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    flavorDimensions += "edition"

    productFlavors {
        create("minimal") {
            dimension = "edition"
            isDefault = true
            resValue("string", "app_name", "Fold8 Launcher")
            buildConfigField("boolean", "IS_MAX_EDITION", "false")
        }
        create("max") {
            dimension = "edition"
            applicationIdSuffix = ".max"
            versionNameSuffix = "-max"
            resValue("string", "app_name", "Fold8 Launcher Max")
            buildConfigField("boolean", "IS_MAX_EDITION", "true")
        }
    }

    signingConfigs {
        create("stable") {
            storeFile = file("fold8-signing.jks")
            storePassword = "foldlauncher"
            keyAlias = "foldlauncher"
            keyPassword = "foldlauncher"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("stable")
        }
        release {
            // Enable R8 full mode minification & resource shrinking for 120Hz zero-bloat performance
            isMinifyEnabled = true
            isShrinkResources = true
            // Use committed deterministic keystore so every GitHub Actions CI build shares the exact same signature
            signingConfig = signingConfigs.getByName("stable")
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Jetpack Compose UI & Material3
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")

    // Jetpack WindowManager for Galaxy Z Fold 8 hinge/posture & dual-screen detection
    implementation("androidx.window:window:1.3.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Unit Testing
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
