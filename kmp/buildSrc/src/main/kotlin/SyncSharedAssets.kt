import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

/**
 * Copies the RN app's shared assets into a generated assets folder: the i18n/locales
 * JSON (locales/), the logo (images/), and the Material Icons font, its glyph map and
 * spec/icons.json (icons/), so both apps show the same text, art and icons.
 */
abstract class SyncSharedAssets : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val locales: DirectoryProperty
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val images: DirectoryProperty
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val vectorIcons: DirectoryProperty
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val spec: DirectoryProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @get:Inject abstract val fs: FileSystemOperations

    @TaskAction
    fun sync() {
        fs.sync {
            from(locales) { into("locales") }
            from(images) { include("logo-full.png"); into("images") }
            from(vectorIcons) {
                include("Fonts/MaterialIcons.ttf", "glyphmaps/MaterialIcons.json")
                eachFile { path = "icons/$name" }
                includeEmptyDirs = false
            }
            from(spec) { include("icons.json"); into("icons") }
            into(outputDir)
        }
    }
}
