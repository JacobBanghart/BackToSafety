plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The RN app's shared assets: locale JSON and images, so both apps show the same text and art.
val repoRoot = rootProject.file("..")
val syncSharedAssets by tasks.registering(SyncSharedAssets::class) {
    locales = repoRoot.resolve("i18n/locales")
    images = repoRoot.resolve("assets/images")
    // node_modules: run `npm ci` in the repo root before building the app.
    vectorIcons = repoRoot.resolve("node_modules/@expo/vector-icons/build/vendor/react-native-vector-icons")
    spec = repoRoot.resolve("spec")
    outputDir = layout.buildDirectory.dir("generated/sharedAssets")
}

val syncLauncherResources by tasks.registering(SyncLauncherResources::class) {
    rnRes = repoRoot.resolve("android/app/src/main/res")
    outputDir = layout.buildDirectory.dir("generated/launcherRes")
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(syncSharedAssets, SyncSharedAssets::outputDir)
        variant.sources.res?.addGeneratedSourceDirectory(syncLauncherResources, SyncLauncherResources::outputDir)
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
        // The RN app's version (app.json), so Settings > About reads the same.
        versionName = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(repoRoot.resolve("app.json").readText())!!.groupValues[1]
        // Test seams (the debug clock), as EXPO_PUBLIC_TEST_SEAMS=1 does for the RN build.
        buildConfigField("boolean", "TEST_SEAMS", (findProperty("testSeams") == "true").toString())
        // PostHog, from the same environment as the RN build; analytics stay off without a key.
        buildConfigField("String", "POSTHOG_KEY", "\"${System.getenv("EXPO_PUBLIC_POSTHOG_KEY").orEmpty()}\"")
        buildConfigField("String", "POSTHOG_HOST", "\"${System.getenv("EXPO_PUBLIC_POSTHOG_HOST") ?: "https://us.i.posthog.com"}\"")
    }

    // The RN repo's debug keystore (local, not committed), so this build can install over the
    // RN test build. Without it (CI), release builds are left unsigned.
    val parityKeystore = repoRoot.resolve("android/app/debug.keystore")
    signingConfigs {
        if (parityKeystore.exists()) {
            create("parity") {
                storeFile = parityKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        val parity = signingConfigs.findByName("parity")
        debug { if (parity != null) signingConfig = parity }
        release {
            signingConfig = parity
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
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
    implementation(libs.android.image.cropper)
    implementation(libs.posthog.android)
    implementation(libs.core.splashscreen)
}
