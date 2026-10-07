// The Kotlin rewrite (PARITY_PLAN.md "Target architecture"): a shared KMP core,
// with Compose on Android and SwiftUI on iOS. It lives next to the RN app so both
// are held to the same spec/ and maestro/ harness until the switch.

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
