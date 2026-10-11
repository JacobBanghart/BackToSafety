// The Android app (Jetpack Compose), on the Rust core in ../core through UniFFI.

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "backtosafety"
include(":app")
