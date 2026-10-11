import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Runs cargo from the Rust workspace (rust/). Cargo does its own up-to-date checks, so these
 * tasks always run; what they produce is hashed by the tasks downstream, so nothing downstream
 * reruns unless the output really changed.
 *
 * Builds are queued one at a time across worktrees (rust/scripts/heavy.sh). Under heavy.sh
 * already (NIJII_HEAVY is set), cargo runs directly.
 */
abstract class CargoTask : DefaultTask() {
    @get:Internal abstract val workspace: DirectoryProperty

    @get:Input abstract val cargoArgs: ListProperty<String>

    /** The Rust sources, so the tasks can be cached by their inputs. */
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val sources: ConfigurableFileCollection

    @get:Internal abstract val heavyScript: RegularFileProperty

    protected fun run(vararg command: String) {
        val prefix = if (System.getenv("NIJII_HEAVY") != null) emptyList() else listOf(heavyScript.get().asFile.path)
        val process = ProcessBuilder(prefix + command.toList())
            .directory(workspace.get().asFile)
            .inheritIO()
            .start()
        if (process.waitFor() != 0) throw GradleException("${command.joinToString(" ")} failed")
    }
}

/** `cargo build` for this machine: the library the JVM tests load (JNA) and uniffi-bindgen. */
abstract class CargoHostBuild : CargoTask() {
    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun build() = run("cargo", "build", "-p", "nijii-core", "-p", "uniffi-bindgen")
}

/** `cargo ndk` for the ABIs the app ships: lib<crate>.so under <outputDir>/<abi>/. */
abstract class CargoNdkBuild : CargoTask() {
    @get:Input abstract val abis: ListProperty<String>

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun build() {
        val targets = abis.get().flatMap { listOf("-t", it) }
        run("cargo", "ndk", *targets.toTypedArray(), "-o", outputDir.get().asFile.path, "build", "-p", "nijii-core", "--release")
    }
}

/** Kotlin bindings for the library, in UniFFI's library mode, into the build directory. */
abstract class UniffiKotlinBindings : DefaultTask() {
    /** cargo metadata, which finds core/uniffi.toml, runs here. */
    @get:Internal abstract val workspace: DirectoryProperty

    /** The host library built by [CargoHostBuild]. */
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val library: RegularFileProperty

    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val bindgen: RegularFileProperty

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val out: File = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        // --no-format: the bindings are the same on every machine, with or without ktlint.
        val process = ProcessBuilder(
            bindgen.get().asFile.path, "generate", "--library", library.get().asFile.path,
            "--language", "kotlin", "--no-format", "--out-dir", out.path,
        ).directory(workspace.get().asFile).redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader().readText()
        if (process.waitFor() != 0) throw GradleException("uniffi-bindgen failed:\n$log")
    }
}
