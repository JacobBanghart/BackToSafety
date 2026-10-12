package com.backtosafety.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Swift calls the shared core's suspend functions as `async throws`, but Kotlin/Native only
 * turns a Kotlin exception into a Swift error when the function says so with @Throws; any
 * other exception (a failed write, say) ends the app, and the do/catch around the call never
 * runs. So every public suspend function in the shared code carries @Throws. The DAOs are
 * left out: only the Store calls them.
 */
class ThrowsGuardTest {
    private val root = File(System.getProperty("repoRoot") ?: error("repoRoot system property not set"))

    private val publicSuspendFun = Regex("""^\s*(?:(?:public|override|open|inline|operator)\s+)*suspend\s+fun\s+(\w+)""")

    private fun missingThrows(file: File): List<String> {
        val lines = file.readLines()
        return lines.indices.mapNotNull { i ->
            val name = publicSuspendFun.find(lines[i])?.groupValues?.get(1) ?: return@mapNotNull null
            // The annotations directly above the declaration (KDoc and comments sit above those).
            val annotations = lines.subList(0, i).asReversed().takeWhile { it.trim().startsWith("@") }
            if (annotations.none { it.trim().startsWith("@Throws") }) "${file.name}:${i + 1} $name" else null
        }
    }

    @Test
    fun everyPublicSuspendFunctionDeclaresThrows() {
        val files = File(root, "kmp/shared/src/commonMain").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && "@Dao" !in it.readText() }
            .toList()
        assertTrue(files.any { it.name == "Store.kt" }, "the sources were not found under $root")
        assertEquals(emptyList(), files.flatMap(::missingThrows), "public suspend functions without @Throws")
    }
}
