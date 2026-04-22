plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.otpforwarder"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.otpforwarder"
        minSdk = 23
        targetSdk = 36
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
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

    // ❌ NO COMPOSE (not needed)
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx.v1120)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.google.gson)
    testImplementation("junit:junit:4.13.2")
}