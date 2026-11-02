plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersion = "0.1.0" // x-release-please-version

android {
    namespace = "dot.vpn.payam"
    compileSdk = 35

    defaultConfig {
        applicationId = "dot.vpn.payam"
        minSdk = 26
        targetSdk = 35
        // MAJOR*10000 + MINOR*100 + PATCH, so every release raises it.
        versionCode = appVersion.split(".").map(String::toInt).let { (a, b, c) -> a * 10000 + b * 100 + c }
        versionName = appVersion
        // The Go library ships arm64 and x86_64 only (see Makefile).
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Release signing comes from CI secrets via environment variables;
        // nothing sensitive lives in the repo.
        val store = System.getenv("DOTVPN_KEYSTORE")
        if (store != null) {
            create("release") {
                storeFile = file(store)
                storePassword = System.getenv("DOTVPN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DOTVPN_KEY_ALIAS")
                keyPassword = System.getenv("DOTVPN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        // Go's shared object must stay uncompressed and aligned for fast load.
        jniLibs.useLegacyPackaging = false
    }
}

dependencies {
    // Built by `make aar` from ../dotvpn-client/mobile.
    implementation(files("libs/dotvpn.aar"))

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    // ML Kit code scanner: no camera permission, falls back to paste.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
