import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.wunding.personachat"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wunding.personachat"
        minSdk = 26
        targetSdk = 34
        versionCode = 17
        versionName = "1.6.8"
        vectorDrawables { useSupportLibrary = true }
    }

    val ksProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    val storePass = System.getenv("PC_KEYSTORE_PASSWORD") ?: ksProps.getProperty("storePassword") ?: ""
    val keyPass = System.getenv("PC_KEY_PASSWORD") ?: ksProps.getProperty("keyPassword") ?: storePass
    val alias = System.getenv("PC_KEY_ALIAS") ?: ksProps.getProperty("keyAlias") ?: "personachat"
    val storeFileObj = rootProject.file("keystore/personachat.jks")

    signingConfigs {
        create("release") {
            if (storeFileObj.exists() && storePass.isNotEmpty()) {
                storeFile = storeFileObj
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (storeFileObj.exists() && storePass.isNotEmpty()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    androidResources { noCompress += listOf("gguf", "task") }
    defaultConfig { ndk { abiFilters += listOf("arm64-v8a") } }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.6.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("com.google.mediapipe:tasks-genai:0.10.24")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
