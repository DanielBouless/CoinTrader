plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.cointrader.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cointrader.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // ── Coinbase OAuth2 app credentials ──────────────────────────────────
        // Register your OAuth app at https://www.coinbase.com/settings/api
        // Set the redirect URI to: cointrader://oauth/callback
        buildConfigField("String", "COINBASE_CLIENT_ID",     "\"YOUR_CLIENT_ID_HERE\"")
        buildConfigField("String", "COINBASE_CLIENT_SECRET", "\"YOUR_CLIENT_SECRET_HERE\"")
        buildConfigField("String", "COINBASE_REDIRECT_URI",  "\"cointrader://oauth/callback\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
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
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.browser)          // Chrome Custom Tabs for OAuth login
    debugImplementation(libs.androidx.ui.tooling)
}
