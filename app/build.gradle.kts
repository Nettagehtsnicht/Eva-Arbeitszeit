plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.eva.arbeitszeit"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.eva.arbeitszeit"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    // Fester Schlüssel, damit spätere Versionen einfach drüber installiert werden können
    signingConfigs {
        create("eva") {
            storeFile = file("eva.keystore")
            storePassword = "evaapp2026"
            keyAlias = "eva"
            keyPassword = "evaapp2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("eva")
        }
        debug {
            signingConfig = signingConfigs.getByName("eva")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
