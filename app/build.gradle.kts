plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Numer wersji nadaje GitHub Actions (kolejny numer budowania). Lokalnie = 1.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// Klucz podpisu z sekretów GitHuba. Wszystkie aktualizacje muszą mieć ten sam podpis,
// inaczej telewizor odmówi instalacji nowej wersji na starą.
val keystorePath: String? = System.getenv("SIGNING_KEYSTORE_FILE")

android {
    namespace = "pl.dom.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "pl.dom.launcher"
        minSdk = 28
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
        // Repozytorium, z którego launcher pobiera aktualizacje.
        buildConfigField("String", "UPDATE_REPO", "\"bepropl/dom-launcher\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Optymalizacja R8 (szybsze działanie). Na razie tylko w wersji testowej z gałęzi dev
            // (MINIFY=1 ustawia workflow); wydania OTA z main zostają bez niej, dopóki nie sprawdzimy.
            val minify = System.getenv("MINIFY") == "1"
            isMinifyEnabled = minify
            isShrinkResources = minify
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystorePath != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
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
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.tv.material)

    implementation(libs.nanohttpd)
    implementation(libs.zxing.core)
    implementation(libs.coil.compose)
    implementation(libs.okhttp)
}
