import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * spec/analytics-events.json (the RN app's AnalyticsEventName, pinned by
 * spec/analytics-events.test.ts) -> AnalyticsEvent.kt. The Kotlin apps can only send an
 * event the spec lists, under exactly its name: dashboards key on these.
 */
abstract class GenerateAnalyticsEvents : DefaultTask() {
    @get:InputFile abstract val spec: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        @Suppress("UNCHECKED_CAST")
        val events = (JsonSlurper().parse(spec.get().asFile) as Map<String, Any>)["events"] as List<String>
        val out = StringBuilder()
        out.appendLine("package com.backtosafety.core")
        out.appendLine()
        out.appendLine("// Generated from spec/analytics-events.json by :shared:generateAnalyticsEvents. Do not edit.")
        out.appendLine()
        out.appendLine("/** The PostHog events both apps send; [wireName] is what dashboards see. */")
        out.appendLine("enum class AnalyticsEvent(val wireName: String) {")
        events.forEach { out.appendLine("    ${it.uppercase()}(\"$it\"),") }
        out.appendLine("}")
        val file = outputDir.get().asFile.resolve("com/backtosafety/core/AnalyticsEvent.kt")
        file.parentFile.mkdirs()
        file.writeText(out.toString())
    }
}
