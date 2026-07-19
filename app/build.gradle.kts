import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Читаем ключ MapKit из local.properties, чтобы не хранить его в git.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// Ключ берётся из local.properties (не в git). Если его там нет — используется
// значение по умолчанию ниже, чтобы проект собирался сразу после клона.
// Yandex MapKit-ключ привязан к пакету приложения, поэтому в APK он и так открыт.
val defaultYandexKey = "c2acd071-9e1b-445f-b2c0-267cb8ab8497"
val yandexKey: String = localProps.getProperty("YANDEX_MAPKIT_API_KEY")
    ?.takeIf { it.isNotBlank() && it != "PUT_YOUR_KEY_HERE" }
    ?: defaultYandexKey

android {
    namespace = "com.spyf.geowalker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.spyf.geowalker"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "YANDEX_MAPKIT_API_KEY", "\"$yandexKey\"")
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

    buildFeatures {
        buildConfig = true
        viewBinding = true
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.code.gson:gson:2.11.0")

    // Yandex MapKit (полная версия). Требует ключ MapKit Mobile SDK.
    implementation("com.yandex.android:maps.mobile:4.6.1-full")
}
