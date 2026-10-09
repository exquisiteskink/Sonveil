plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "app.sonveil.music"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.sonveil.music"
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 33
        versionName = "1.3.13"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("test") {
            storeFile = rootProject.file("keystore/debug.jks")
            storePassword = "auralis-debug"
            keyAlias = "auralis"
            keyPassword = "auralis-debug"
        }
    }

    buildTypes {
        debug {
            signingConfig = if (rootProject.file("keystore/debug.jks").isFile) {
                signingConfigs.getByName("test")
            } else {
                signingConfigs.getByName("debug")
            }
            applicationIdSuffix = ""
            isMinifyEnabled = false
        }
        release {
            // Release signing must be supplied privately, never with the shared test key.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=androidx.media3.common.util.UnstableApi")
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
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    val media3 = "1.5.1"

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
