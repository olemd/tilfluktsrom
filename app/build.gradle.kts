import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Read version from shared version.properties
val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}

android {
    namespace = "no.naiv.tilfluktsrom"
    compileSdk = 35

    defaultConfig {
        applicationId = "no.naiv.tilfluktsrom"
        minSdk = 26
        targetSdk = 35
        versionCode = versionProps.getProperty("versionCode").toInt()
        versionName = "${versionProps.getProperty("versionMajor")}." +
            "${versionProps.getProperty("versionMinor")}." +
            versionProps.getProperty("versionPatch")

        // Make version available in BuildConfig
        buildConfigField("String", "VERSION_DISPLAY", "\"$versionName\"")
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getProperty("user.home") + "/.android/tilfluktsrom-release.jks"
            if (file(keystorePath).exists()) {
                storeFile = file(keystorePath)
                storePassword = "tilfluktsrom"
                keyAlias = "tilfluktsrom"
                keyPassword = "tilfluktsrom"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // AndroidX
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    // Room (local database for shelter cache)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // OkHttp (HTTP client for data downloads)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // OSMDroid (offline-capable OpenStreetMap)
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // Google Play Services Location (precise GPS)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // WorkManager (periodic widget updates)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
