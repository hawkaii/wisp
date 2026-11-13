plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.wispnotes"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.wispnotes"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0" // CI reads this for the release tag and APK name
    }

    signingConfigs {
        // shortcut: committed throwaway keystore (not a secret) so every CI APK has the
        // same signature and installs over the last one; replace with a real key before Play.
        create("wisp") {
            storeFile = file("wisp-debug.jks")
            storePassword = "wispdebug"
            keyAlias = "wisp"
            keyPassword = "wispdebug"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("wisp")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true // Settings shows the version
    }
    lint { abortOnError = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.coil.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.org.json) // android.jar stubs org.json on the JVM
}
