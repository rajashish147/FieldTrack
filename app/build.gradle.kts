import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) {
        file.inputStream().use(::load)
    }
}

val productionApiBaseUrl = "https://api.getfieldtrack.app/"

fun quotedApiBaseUrl(url: String): String {
    require(url.endsWith("/")) {
        "API_BASE_URL must end with '/'. Current value: $url"
    }
    return "\"$url\""
}

fun localApiBaseUrlOr(defaultUrl: String): String =
    localProperties.getProperty("API_BASE_URL")?.trim()?.takeIf { it.isNotEmpty() }
        ?: defaultUrl

android {
    namespace   = "com.fieldtrack.app"
    compileSdk  = 35

    defaultConfig {
        applicationId = "com.fieldtrack.app"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 1
        versionName   = "2.0.0"

        buildConfigField("String", "API_BASE_URL", quotedApiBaseUrl(productionApiBaseUrl))
    }

    buildTypes {
        debug {
            // Optional local dev override: API_BASE_URL=http://10.0.2.2:3000/
            buildConfigField("String", "API_BASE_URL", quotedApiBaseUrl(localApiBaseUrlOr(productionApiBaseUrl)))
            isDebuggable    = true
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled   = true
            isShrinkResources = true  // Remove unused resources — reduces APK size
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Signing config: configure signingConfigs block and reference here
            // before generating signed AAB/APK for production.
            // signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Desugaring: enables java.time APIs on API < 26 (unnecessary here since minSdk=26,
        // but safe to leave for library compatibility)
    }

    kotlinOptions {
        jvmTarget = "17"
        // Disable debugging metadata in release (reduces APK size, improves obfuscation)
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    packaging {
        resources {
            // Exclude duplicated license files from transitive dependencies
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
        jniLibs {
            keepDebugSymbols += "**/libandroidx.graphics.path.so"
            keepDebugSymbols += "**/libdatastore_shared_counter.so"
        }
    }
}

dependencies {
    // Compose BOM — pins all Compose library versions together
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.google.fonts)
    debugImplementation(libs.compose.ui.tooling)

    // Core
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Retrofit + OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Location
    implementation(libs.play.services.location)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // Maps
    implementation(libs.osmdroid)

    // Charts
    implementation(libs.mpandroidchart)

    // Coil
    implementation(libs.coil.compose)

    // DataStore
    implementation(libs.datastore.preferences)
}
