// SPDX-License-Identifier: AGPL-3.0-only

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersion = "0.4.0" // x-release-please-version

android {
    namespace = "dot.vpn.payam"
    compileSdk = 37

    defaultConfig {
        applicationId = "dot.vpn.payam"
        minSdk = 26
        targetSdk = 35
        // MAJOR*10000 + MINOR*100 + PATCH, so every release raises it.
        versionCode = appVersion.split(".").map(String::toInt).let { (a, b, c) -> a * 10000 + b * 100 + c }
        versionName = appVersion
        // Release CI may inject SENTRY_DSN_ANDROID; empty keeps crash reporting no-op.
        buildConfigField("String", "SENTRY_DSN", "\"${System.getenv("SENTRY_DSN_ANDROID") ?: ""}\"")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        // Go's shared object must stay uncompressed and aligned for fast load.
        jniLibs.useLegacyPackaging = false
    }
}

dependencies {
    // Built by `make aar` from ../dotvpn-client/mobile.
    implementation(files("libs/dotvpn.aar"))

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    // ML Kit: GmsBarcodeScanning (camera) + BarcodeScanning (gallery bitmap).
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("io.sentry:sentry-android:7.22.4")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")

    testImplementation("junit:junit:4.13.2")
    // JVM unit tests: Android's org.json is not on the classpath.
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}

// android.jar stubs org.json.*; put the pure-Java artifact ahead of it.
tasks.withType<Test>().configureEach {
    val (androidJars, rest) = classpath.files.partition { it.name == "android.jar" }
    classpath = files(rest) + files(androidJars)
}
