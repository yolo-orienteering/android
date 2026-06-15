plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "ch.seccom.omate"
    compileSdk = 35

    defaultConfig {
        applicationId = "ch.seccom.omate"
        minSdk = 24
        targetSdk = 35
        // versionCode is reported to the web app via the User-Agent (o-mate-app/<versionCode>).
        // Calendar/webcal handling was added in versionCode 2; the frontend gates that feature
        // on it — keep in lockstep with the iOS CFBundleVersion (see frontend useNativeApp).
        versionCode = 3
        versionName = "3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        // Generates BuildConfig so START_URL (set per build type below) is available in code.
        buildConfig = true
    }

    buildTypes {
        debug {
            // Make sure the url is the one available in your wifi.
            val devStartUrl = (project.findProperty("devStartUrl") as String?)
                ?: System.getenv("DEV_START_URL")
                ?: "http://192.168.0.20:3000/"
            buildConfigField("String", "START_URL", "\"$devStartUrl\"")
        }
        release {
            // Production environment — used by `./gradlew assembleRelease` / bundleRelease.
            // Override via -PprodStartUrl=... or env var PROD_START_URL=...
            val prodStartUrl = (project.findProperty("prodStartUrl") as String?)
                ?: System.getenv("PROD_START_URL")
                ?: "https://o-mate.app"
            buildConfigField("String", "START_URL", "\"$prodStartUrl\"")

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
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}