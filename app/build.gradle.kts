plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.georgewitte.yep"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.georgewitte.yep"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
}

dependencies {
    // No runtime libraries on purpose: the app only uses the Android framework and Kotlin.
    testImplementation(libs.junit)
}
