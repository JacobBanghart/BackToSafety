
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val generateDesignTokens = tasks.register<GenerateDesignTokens>("generateDesignTokens") {
    spec = rootProject.file("../spec/design-tokens.json")
    outputDir = layout.buildDirectory.dir("generated/tokens/commonMain/kotlin")
}

val generateAnalyticsEvents = tasks.register<GenerateAnalyticsEvents>("generateAnalyticsEvents") {
    spec = rootProject.file("../spec/analytics-events.json")
    outputDir = layout.buildDirectory.dir("generated/analytics/commonMain/kotlin")
}

kotlin {
    sourceSets.commonMain.configure {
        kotlin.srcDir(generateDesignTokens)
        kotlin.srcDir(generateAnalyticsEvents)
    }
    android {
        namespace = "com.backtosafety.core"
        compileSdk = 37
        minSdk = 24
    }
    // Plain JVM target: the vector tests run here, on the devbox, in seconds.
    jvm()
    // The SwiftUI app links the core as a static framework, which its first Xcode build phase
    // builds (embedAndSignAppleFrameworkForXcode) for the configuration and SDK being built.
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
            api(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

// Room's generated code for every target; the exported schemas are reviewed in git.
room {
    schemaDirectory("$projectDir/schemas")
}
dependencies {
    listOf("kspAndroid", "kspJvm", "kspIosArm64", "kspIosSimulatorArm64").forEach {
        add(it, libs.room.compiler)
    }
}

// The JVM tests read spec/vectors and i18n/locales straight from the repo.
tasks.withType<Test>().configureEach {
    systemProperty("repoRoot", rootDir.parentFile.absolutePath)
}
