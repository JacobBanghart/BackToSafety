plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    androidLibrary {
        namespace = "com.backtosafety.core"
        compileSdk = 36
        minSdk = 24
    }
    // Plain JVM target: the vector tests run here, on the devbox, in seconds.
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// The JVM tests read spec/vectors and i18n/locales straight from the repo.
tasks.withType<Test>().configureEach {
    systemProperty("repoRoot", rootDir.parentFile.absolutePath)
}
