import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * The RN app's launcher icon and splash logo (android/app/src/main/res), so both apps look the
 * same on the home screen and while starting.
 */
abstract class SyncLauncherResources : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val rnRes: DirectoryProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @get:Inject abstract val fs: FileSystemOperations

    @TaskAction
    fun sync() {
        fs.sync {
            from(rnRes) { include("mipmap-*/ic_launcher*", "drawable-*/splashscreen_logo.png", "drawable/ic_launcher_background.xml") }
            into(outputDir)
        }
    }
}
