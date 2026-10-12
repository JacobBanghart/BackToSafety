// The Kotlin build (docs/ARCHITECTURE.md): a shared KMP core, with Compose on Android
// and SwiftUI on iOS, both held to the spec/ and maestro/ harness.

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
include(":shared")
include(":androidApp")
