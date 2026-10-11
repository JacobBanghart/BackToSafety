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

// The Rust core (rust/core) through UniFFI. cargo builds the library for this machine (the JVM
// tests load it, and the Kotlin bindings are generated from it) and, with cargo-ndk, the
// Android libraries the app ships. All of it runs through rust/scripts/heavy.sh's queue.
val rustWorkspace = repoRoot.resolve("rust")
val cargoTargetDir = System.getenv("CARGO_TARGET_DIR") ?: "${System.getProperty("user.home")}/.cache/nijii-rust-target"
val rustSources = fileTree(rustWorkspace) {
    include("Cargo.toml", "Cargo.lock", "core/**", "uniffi-bindgen/**")
    exclude("**/target/**")
}
val specFiles = fileTree(repoRoot.resolve("spec")) { include("analytics-events.json") }
val heavy = rustWorkspace.resolve("scripts/heavy.sh")

val buildRustHost = tasks.register<CargoHostBuild>("buildRustHost") {
    workspace = rustWorkspace
    heavyScript = heavy
    sources.from(rustSources, specFiles)
}

val buildRustAndroid = tasks.register<CargoNdkBuild>("buildRustAndroid") {
    workspace = rustWorkspace
    heavyScript = heavy
    sources.from(rustSources, specFiles)
    abis = listOf("arm64-v8a", "x86_64")
    outputDir = layout.buildDirectory.dir("generated/rustJniLibs")
}

val generateCoreBindings = tasks.register<UniffiKotlinBindings>("generateCoreBindings") {
    dependsOn(buildRustHost)
    workspace = rustWorkspace
    library = file("$cargoTargetDir/debug/libnijii_core.so")
    bindgen = file("$cargoTargetDir/debug/uniffi-bindgen")
    outputDir = layout.buildDirectory.dir("generated/uniffi/kotlin")
}

// DesignTokens is UI, so it's generated here from spec/design-tokens.json, not in the core.
val generateDesignTokens = tasks.register<GenerateDesignTokens>("generateDesignTokens") {
    spec = repoRoot.resolve("spec/design-tokens.json")
    packageName = "com.backtosafety.app.core"
    outputDir = layout.buildDirectory.dir("generated/tokens/kotlin")
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(syncSharedAssets, SyncSharedAssets::outputDir)
        variant.sources.jniLibs?.addGeneratedSourceDirectory(buildRustAndroid, CargoNdkBuild::outputDir)
        variant.sources.kotlin?.addGeneratedSourceDirectory(generateCoreBindings, UniffiKotlinBindings::outputDir)
        variant.sources.kotlin?.addGeneratedSourceDirectory(generateDesignTokens, GenerateDesignTokens::outputDir)
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
        // The ABIs the Rust core is built for.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
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
            // R8: shrinks and optimises code and drops unused resources. The libraries bring
            // their own keep rules; proguard-rules.pro has the app's.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Snapshot tests: `./gradlew :androidApp:testDebugUnitTest` compares every screen with its
    // reference image in src/test/snapshots; -Precord writes new references instead.
    testOptions.unitTests {
        isIncludeAndroidResources = true
        all {
            it.systemProperty("repoRoot", repoRoot.absolutePath)
            // The Rust core for this machine, which JNA loads in the JVM.
            it.systemProperty("jna.library.path", "$cargoTargetDir/debug")
            it.dependsOn(buildRustHost)
            val record = project.hasProperty("record")
            it.systemProperty("roborazzi.test.record", record.toString())
            it.systemProperty("roborazzi.test.verify", (!record).toString())
        }
    }
}

dependencies {
    implementation(libs.jna) { artifact { type = "aar" } }
    implementation(libs.kotlinx.datetime)
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

    testImplementation(libs.jna)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
