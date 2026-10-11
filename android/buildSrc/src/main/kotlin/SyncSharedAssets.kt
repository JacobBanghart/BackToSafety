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
 * Copies the assets the Android and iOS apps share into a generated assets folder: the
 * i18n/locales JSON (locales/), the logo (images/) and spec/icons.json (icons/). The Material
 * Icons font and its glyph map live in src/main/assets/icons.
 */
abstract class SyncSharedAssets : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val locales: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val images: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val spec: DirectoryProperty

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @get:Inject abstract val fs: FileSystemOperations

    @TaskAction
    fun sync() {
        fs.sync {
            from(locales) { into("locales") }
            from(images) { include("logo-full.png"); into("images") }
            from(spec) { include("icons.json"); into("icons") }
            into(outputDir)
        }
    }
}
