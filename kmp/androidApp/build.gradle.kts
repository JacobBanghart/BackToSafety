plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The RN app's shared assets: locale JSON and images, so both apps show the same text and art.
val repoRoot = rootProject.file("..")
val syncSharedAssets by tasks.registering(SyncSharedAssets::class) {
    locales = repoRoot.resolve("i18n/locales")
    images = repoRoot.resolve("assets/images")
    outputDir = layout.buildDirectory.dir("generated/sharedAssets")
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(syncSharedAssets, SyncSharedAssets::outputDir)
    }
}

android {
    namespace = "com.backtosafety.app"
    compileSdk = 37

    defaultConfig {
        // Same ID as the RN app: the Kotlin app installs over it (PARITY_PLAN L5).
        applicationId = "com.backtosafety.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 100
        versionName = "2.0.0-dev"
    }

    signingConfigs {
        // The RN repo's debug keystore, so this build can install over the RN test build.
        create("parity") {
            storeFile = repoRoot.resolve("android/app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("parity") }
        release {
            signingConfig = signingConfigs.getByName("parity")
            isMinifyEnabled = false
        }
    }

    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
}
