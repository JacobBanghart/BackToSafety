import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * spec/design-tokens.json (pinned to the RN app's constants/ by spec/design-tokens.test.ts)
 * -> DesignTokens.kt, so the Kotlin UIs use exactly the RN app's colors, spacing, radii
 * and type scale.
 */
abstract class GenerateDesignTokens : DefaultTask() {
    @get:InputFile abstract val spec: RegularFileProperty

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        @Suppress("UNCHECKED_CAST")
        val tokens = JsonSlurper().parse(spec.get().asFile) as Map<String, Any>
        val out = StringBuilder()
        out.appendLine("package com.backtosafety.core")
        out.appendLine()
        out.appendLine("// Generated from spec/design-tokens.json by :shared:generateDesignTokens. Do not edit.")
        out.appendLine()
        out.appendLine("object DesignTokens {")

        fun colors(name: String, map: Map<*, *>) {
            out.appendLine("    object $name {")
            map.forEach { (k, v) -> if (v is String) out.appendLine("        const val ${ident(k.toString())}: Long = ${argb(v)}") }
            out.appendLine("    }")
        }
        val themeColors = tokens["colors"] as Map<*, *>
        colors("Light", themeColors["light"] as Map<*, *>)
        colors("Dark", themeColors["dark"] as Map<*, *>)
        (tokens["palette"] as Map<*, *>).forEach { (name, map) ->
            colors(name.toString().replaceFirstChar { it.uppercase() }, map as Map<*, *>)
        }

        fun numbers(name: String, map: Map<*, *>) {
            out.appendLine("    object $name {")
            map.forEach { (k, v) -> out.appendLine("        const val $k: Float = ${(v as Number).toFloat()}f") }
            out.appendLine("    }")
        }
        numbers("Spacing", tokens["spacing"] as Map<*, *>)
        numbers("Radius", tokens["radius"] as Map<*, *>)

        out.appendLine("    class Type(val fontSize: Float, val fontWeight: Int, val lineHeight: Float, val letterSpacing: Float)")
        out.appendLine("    object Typography {")
        (tokens["typography"] as Map<*, *>).forEach { (k, v) ->
            val t = v as Map<*, *>
            out.appendLine(
                "        val $k = Type(${(t["fontSize"] as Number).toFloat()}f, ${(t["fontWeight"] as String).toInt()}, " +
                    "${(t["lineHeight"] as Number).toFloat()}f, ${(t["letterSpacing"] as Number).toFloat()}f)",
            )
        }
        out.appendLine("    }")
        out.appendLine("}")

        val dir = outputDir.get().asFile.resolve("com/backtosafety/core")
        dir.mkdirs()
        dir.resolve("DesignTokens.kt").writeText(out.toString())
    }

    private fun ident(key: String) = if (key.first().isDigit()) "c$key" else key

    /** "#3f2875" or "rgba(0,0,0,0.4)" -> 0xAARRGGBB. */
    private fun argb(css: String): String {
        val rgba = Regex("""rgba\((\d+),\s*(\d+),\s*(\d+),\s*([\d.]+)\)""").matchEntire(css)
        val value = if (rgba != null) {
            val (r, g, b, a) = rgba.destructured
            (Math.round(a.toDouble() * 255) shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
        } else {
            0xFF000000L or css.removePrefix("#").toLong(16)
        }
        return "0x" + value.toString(16).uppercase().padStart(8, '0')
    }
}
