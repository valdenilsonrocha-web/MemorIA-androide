import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing. The credentials live OUTSIDE the repo — in keystore.properties
// (git-ignored) or in the MEMORIA_KEYSTORE_* environment variables for CI. When
// neither is present the release build still succeeds, it just produces an
// unsigned APK, so a fresh clone is not blocked by a missing key.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(property: String, environmentVariable: String): String? =
    (keystoreProps.getProperty(property) ?: System.getenv(environmentVariable))
        ?.takeIf { it.isNotBlank() }

android {
    namespace = "com.memoria.mobile"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.memoria.mobile"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // Default public backend base URL (no trailing slash, WITHOUT /api — the
        // client appends /api, mirroring the web frontend's runtime-config.js).
        // Overridable at runtime in Settings.
        //
        // Points at the GCP deployment. Use the nip.io name, NOT the bare IP:
        // the Let's Encrypt cert is issued for CN=35.247.217.66.nip.io, so
        // https://35.247.217.66 fails hostname verification on Android
        // (nip.io resolves the embedded IP, no DNS record to maintain).
        buildConfigField(
            "String",
            "DEFAULT_API_BASE_URL",
            "\"https://35.247.217.66.nip.io\""
        )
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        signingValue("storeFile", "MEMORIA_KEYSTORE_FILE")?.let { path ->
            create("release") {
                storeFile = rootProject.file(path)
                storePassword = signingValue("storePassword", "MEMORIA_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "MEMORIA_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "MEMORIA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Null when no credentials were supplied — the APK is then unsigned.
            signingConfig = signingConfigs.findByName("release")
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
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
}
