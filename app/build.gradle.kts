import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing comes from the CI secrets (see .github/workflows/release.yml).
// Without them, the release build is produced unsigned.
val signingKeystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")

android {
    namespace = "io.github.daniel_noordzij.qrapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.daniel_noordzij.qrapp"
        minSdk = 24
        targetSdk = 36
        versionCode = providers.gradleProperty("qrVersionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("qrVersionName").getOrElse("1.0-dev")

        // ARM covers practically every Android phone and tablet. Leaving out x86/x86_64
        // (emulators, a few Intel devices) halves the APK, since ML Kit ships large
        // native libraries per CPU type.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    signingConfigs {
        if (signingKeystorePath != null) {
            create("release") {
                storeFile = file(signingKeystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Don't embed the Google-encrypted dependency metadata block in the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.mlkit.vision)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.text.recognition)

    implementation(libs.androidx.exifinterface)
}
