plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Assets shared with the iOS app: locale JSON and images, so both apps show the same text and art.
val repoRoot = rootProject.file("..")
val syncSharedAssets = tasks.register<SyncSharedAssets>("syncSharedAssets") {
    locales = repoRoot.resolve("i18n/locales")
    images = repoRoot.resolve("assets/images")
    spec = repoRoot.resolve("spec")
    outputDir = layout.buildDirectory.dir("generated/sharedAssets")
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(syncSharedAssets, SyncSharedAssets::outputDir)
    }
}

/** version.json: the version and build numbers both apps ship with. */
val release = groovy.json.JsonSlurper().parse(repoRoot.resolve("version.json")) as Map<*, *>

android {
    namespace = "com.backtosafety.app"
    compileSdk = 37

    defaultConfig {
        // Same ID as the RN app: the Kotlin app installs over it (PARITY_PLAN L5).
        applicationId = "com.backtosafety.app"
        minSdk = 24
        targetSdk = 36
        // Version and build number come from version.json: the release workflow stamps the
        // tag's version and the run number there before building.
        versionCode = (release["android"] as Map<*, *>)["versionCode"].toString().toInt()
        versionName = release["version"] as String
        // Test seams (the debug clock) for the Maestro harness: -PtestSeams=true.
        buildConfigField("boolean", "TEST_SEAMS", (findProperty("testSeams") == "true").toString())
        // PostHog, from the build environment; analytics stay off without a key.
        buildConfigField("String", "POSTHOG_KEY", "\"${System.getenv("POSTHOG_KEY").orEmpty()}\"")
        buildConfigField("String", "POSTHOG_HOST", "\"${System.getenv("POSTHOG_HOST") ?: "https://us.i.posthog.com"}\"")
    }

    // The Play upload key, from android-release.yml (the same secrets the RN release used).
    val uploadKeystore = System.getenv("ANDROID_KEYSTORE_FILE")?.let(::file)
    signingConfigs {
        if (uploadKeystore != null) {
            create("upload") {
                storeFile = uploadKeystore
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Without the upload key (local and harness builds), the debug key, so the APK installs.
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
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
