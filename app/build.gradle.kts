plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.linkscope"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.linkscope"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.6"
    }

    // Signing key lives outside the repo. CI writes it from the KEYSTORE_BASE64
    // secret; locally, put it at ~/.linkscope/sideload.keystore or set the env vars.
    val ksFile = System.getenv("LINKSCOPE_KEYSTORE")?.let { file(it) }
        ?: file("${System.getProperty("user.home")}/.linkscope/sideload.keystore")
    val ksPass = System.getenv("LINKSCOPE_KEYSTORE_PASSWORD")
    val hasKey = ksFile.exists() && ksPass != null
    signingConfigs {
        if (hasKey) create("sideload") {
            storeFile = ksFile
            storePassword = ksPass
            keyAlias = System.getenv("LINKSCOPE_KEY_ALIAS") ?: "linkscope"
            keyPassword = ksPass
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (hasKey) signingConfig = signingConfigs.getByName("sideload")
        }
        debug {
            if (hasKey) signingConfig = signingConfigs.getByName("sideload")
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
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
}
