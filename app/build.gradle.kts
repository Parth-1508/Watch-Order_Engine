import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.watchorderengine"
    compileSdk = 35

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(localPropertiesFile.inputStream())
    }

    defaultConfig {
        applicationId = "com.example.watchorderengine"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "TMDB_API_KEY", "\"${localProperties.getProperty("TMDB_API_KEY") ?: ""}\"")
        buildConfigField("String", "TMDB_READ_ACCESS_TOKEN", "\"${localProperties.getProperty("TMDB_READ_ACCESS_TOKEN") ?: ""}\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"${localProperties.getProperty("GEMINI_API_KEY") ?: ""}\"")
        buildConfigField("String", "GEMINI_BURNER_KEY_1", "\"${localProperties.getProperty("GEMINI_BURNER_KEY_1") ?: ""}\"")
        buildConfigField("String", "GEMINI_BURNER_KEY_2", "\"${localProperties.getProperty("GEMINI_BURNER_KEY_2") ?: ""}\"")
        buildConfigField("String", "GEMINI_BURNER_KEY_3", "\"${localProperties.getProperty("GEMINI_BURNER_KEY_3") ?: ""}\"")
        buildConfigField("String", "GEMINI_BURNER_KEY_4", "\"${localProperties.getProperty("GEMINI_BURNER_KEY_4") ?: ""}\"")
        buildConfigField("String", "GEMINI_BURNER_KEY_5", "\"${localProperties.getProperty("GEMINI_BURNER_KEY_5") ?: ""}\"")
        buildConfigField("String", "GEMINI_BURNER_KEY_6", "\"${localProperties.getProperty("GEMINI_BURNER_KEY_6") ?: ""}\"")
        buildConfigField("String", "GEMINI_BURNER_KEY_7", "\"${localProperties.getProperty("GEMINI_BURNER_KEY_7") ?: ""}\"")
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.storage)
    
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.play.services)
    
    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)
    ksp(libs.hilt.compiler)

    // Retrofit + Moshi
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.kotlin.codegen)

    // OkHttp
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.loggingInterceptor)

    // Coil
    implementation(libs.coil.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Gemini
    implementation(libs.generativeai)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.work.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
