// Generates androidApp's Baseline Profile and startup profile on a device, and benchmarks cold
// start and the contacts list with and without it (docs/baseline-profile.md).
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.backtosafety.baselineprofile"
    compileSdk = 37

    defaultConfig {
        // Profiles collected below API 33 need a rooted device.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Emulators run the benchmarks too: their numbers are relative, not a phone's.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    targetProjectPath = ":androidApp"
}

// The connected phone or emulator (ANDROID_SERIAL picks one), not a managed device.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
