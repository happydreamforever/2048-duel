// Root build file. Module builds live in shared/, server/ and android/.
//
// Deliberately NO plugins {} block: modules version their own plugins via gradle/libs.versions.toml.
// Declaring a subset here breaks the android module ("already on the classpath with an unknown
// version"), and declaring all of them would force every build - including server-only builds on
// hosts without JDK 17/Android SDK (-Pduel2048.serverOnly) - to resolve AGP 8, which is Java 17
// bytecode.

// ---------------------------------------------------------------------------------------------
// Offline builds.
//
//   gradlew downloadDependencies   (once, with internet)  -> m2/ + offline/gradle-x.y.z/
//   gradlew-offline(.bat) <tasks>  (anywhere, no internet)
//
// downloadDependencies runs the complete build in a fresh, project-local Gradle home so that
// exactly this project's artifacts (dependencies, Gradle plugins, Kotlin compiler, AGP tools,
// aapt2 for Windows/macOS/Linux) are fetched, then exports that cache as a Maven-layout
// repository which settings.gradle.kts puts first in the repository list.
// ---------------------------------------------------------------------------------------------

val offlineHome: File = rootDir.resolve(".gradle-offline-home")
val offlineRepo: File = rootDir.resolve("m2")
val offlineDistDir: File = rootDir.resolve("offline")

// No String.lowercase() here: build scripts compile against Gradle 7.4.2's embedded Kotlin 1.5.
fun isWindows(): Boolean = System.getProperty("os.name").startsWith("Windows")

/**
 * Copies the Gradle distribution this build is running on into the fresh home so the child
 * build does not download the same ~130 MB zip again. Only works when started via the wrapper.
 */
fun seedGradleDistribution() {
    val home = gradle.gradleHomeDir ?: return                       // .../wrapper/dists/gradle-x-bin/<hash>/gradle-x
    val hashDir = home.parentFile ?: return
    val distsRoot = hashDir.parentFile?.parentFile ?: return
    if (distsRoot.name != "dists" || !hashDir.listFiles().orEmpty().any { it.name.endsWith(".zip.ok") }) return
    val target = offlineHome.resolve("wrapper/dists/${hashDir.parentFile.name}/${hashDir.name}")
    if (target.resolve(home.name).isDirectory) return
    logger.lifecycle("Reusing the current Gradle distribution (${home.name}) for the child build.")
    hashDir.copyRecursively(target, overwrite = true)
    target.resolve("${home.name}/bin/gradle").setExecutable(true)
}

fun runChildGradle(vararg args: String) {
    val serverOnly = providers.gradleProperty("duel2048.serverOnly").isPresent
    val launcher = if (isWindows()) listOf("cmd", "/c", rootDir.resolve("gradlew.bat").absolutePath) else listOf(rootDir.resolve("gradlew").absolutePath)
    val command = launcher + listOf("--no-daemon", "--console=plain", "-Dduel2048.m2=false") +
        (if (serverOnly) listOf("-Pduel2048.serverOnly") else emptyList()) + args
    logger.lifecycle("> child build: ${args.joinToString(" ")}")
    logger.lifecycle("  GRADLE_USER_HOME=$offlineHome")
    val process = ProcessBuilder(command)
        .directory(rootDir)
        .redirectErrorStream(true)
        .also { it.environment()["GRADLE_USER_HOME"] = offlineHome.absolutePath }
        .start()
    // Stream the child's output through this build's logger; inheritIO would go to the daemon, not the console.
    // Also scan for "BUILD FAILED": on Windows the Gradle launcher can return exit code 0 after a failed
    // build, and exporting a broken cache as if it were complete is worse than failing loudly.
    var failed = false
    process.inputStream.bufferedReader().useLines { lines ->
        lines.forEach { line ->
            logger.lifecycle("  | $line")
            if (line.startsWith("BUILD FAILED")) failed = true
        }
    }
    val code = process.waitFor()
    if (code != 0 || failed) {
        throw GradleException("child build failed (exit code $code): ${args.joinToString(" ")}")
    }
}

/**
 * Gradle Module Metadata lists each artifact with a logical `name` (what the dependency cache
 * stores, e.g. `material3-release.aar`) and a repository `url` (what a Maven repository must be
 * called, e.g. `material3-android-1.3.1.aar`). Returns name -> url for one version directory.
 */
@Suppress("UNCHECKED_CAST")
fun artifactRenames(versionDir: File): Map<String, String> {
    val moduleFile = versionDir.walkTopDown().maxDepth(2).firstOrNull { it.isFile && it.name.endsWith(".module") } ?: return emptyMap()
    val renames = HashMap<String, String>()
    try {
        val root = groovy.json.JsonSlurper().parseText(moduleFile.readText()) as Map<String, Any?>
        val variants = root["variants"] as? List<Map<String, Any?>> ?: return emptyMap()
        for (variant in variants) {
            val files = variant["files"] as? List<Map<String, Any?>> ?: continue
            for (f in files) {
                val name = f["name"] as? String ?: continue
                val url = f["url"] as? String ?: continue
                if (name != url) renames[name] = url
            }
        }
    } catch (e: Exception) {
        logger.warn("could not read ${moduleFile}: ${e.message}")
    }
    return renames
}

/**
 * Copies [source] to [target] unless an identical file (same size) is already there. Existing
 * files are updated in place rather than deleted first, so a jar held open by another process
 * (Android Studio, a Gradle daemon) on Windows does not abort the export when it is unchanged.
 */
fun syncFile(source: File, target: File, stats: LongArray) {
    if (target.isFile && target.length() == source.length()) {
        stats[2]++
        return
    }
    target.parentFile.mkdirs()
    try {
        source.copyTo(target, overwrite = true)
        stats[0]++
        stats[1] += source.length()
    } catch (e: Exception) {
        throw GradleException(
            "Cannot write $target (${e.message}). Another program holds it open: close Android Studio, run " +
                "`gradlew --stop`, then re-run. Files already exported are kept.",
        )
    }
}

/** files-2.1/<group>/<module>/<version>/<sha1>/<file>  ->  m2/<group/as/path>/<module>/<version>/<url-name> */
fun exportDependencyCache() {
    val files21 = offlineHome.resolve("caches/modules-2/files-2.1")
    require(files21.isDirectory) { "no dependency cache at $files21; run the child build first" }
    offlineRepo.mkdirs()
    val stats = LongArray(3) // copied, bytes, unchanged
    var renamed = 0
    val wanted = HashSet<File>()
    for (group in files21.listFiles()!!.filter { it.isDirectory }) {
        val groupPath = group.name.replace('.', '/')
        for (module in group.listFiles()!!.filter { it.isDirectory }) {
            for (version in module.listFiles()!!.filter { it.isDirectory }) {
                val renames = artifactRenames(version)
                for (hash in version.listFiles()!!.filter { it.isDirectory }) {
                    for (file in hash.listFiles()!!.filter { it.isFile }) {
                        if (file.name.endsWith("-sources.jar") || file.name.endsWith("-javadoc.jar")) continue
                        val targetName = renames[file.name] ?: file.name
                        if (targetName != file.name) renamed++
                        val target = offlineRepo.resolve("$groupPath/${module.name}/${version.name}/$targetName")
                        wanted += target.absoluteFile
                        syncFile(file, target, stats)
                    }
                }
            }
        }
    }
    // Remove leftovers from older versions; failures (locked files) are only reported.
    var stale = 0
    var locked = 0
    offlineRepo.walkBottomUp().forEach { f ->
        if (f.isFile && f.absoluteFile !in wanted) { if (f.delete()) stale++ else locked++ } else if (f.isDirectory && f != offlineRepo && f.listFiles().isNullOrEmpty()) f.delete()
    }
    logger.lifecycle("m2: ${wanted.size} files (${stats[0]} copied, ${stats[1] / 1_048_576} MB; ${stats[2]} unchanged; $renamed renamed per module metadata; $stale stale removed" + (if (locked > 0) "; $locked stale files locked, delete them later" else "") + ")")
}

fun exportGradleDistribution() {
    val dists = offlineHome.resolve("wrapper/dists")
    val dist = dists.walkTopDown().maxDepth(3).firstOrNull { it.isDirectory && it.name.startsWith("gradle-") && it.resolve("bin").isDirectory }
        ?: gradle.gradleHomeDir?.takeIf { it.resolve("bin").isDirectory }   // already running on the bundled copy
        ?: throw GradleException("no Gradle distribution under $dists")
    val target = offlineDistDir.resolve(dist.name)
    if (dist.canonicalFile == target.canonicalFile) {
        logger.lifecycle("offline/${dist.name}: Gradle distribution already in place")
        return
    }
    val stats = LongArray(3)
    dist.walkTopDown().filter { it.isFile }.forEach { f -> syncFile(f, target.resolve(f.relativeTo(dist).path), stats) }
    target.resolve("bin/gradle").setExecutable(true)
    logger.lifecycle("offline/${dist.name}: Gradle distribution ready (${stats[0]} files copied, ${stats[2]} unchanged)")
}

tasks.register("fetchPlatformArtifacts") {
    description = "Resolves OS-specific tool binaries (aapt2 for windows/osx/linux). Used by downloadDependencies."
    doLast {
        val version = (findProperty("aapt2Version") as String?) ?: throw GradleException("-Paapt2Version=<version> is required")
        for (os in listOf("windows", "osx", "linux")) {
            val cfg = configurations.detachedConfiguration(dependencies.create("com.android.tools.build:aapt2:$version:$os"))
            cfg.isTransitive = false
            logger.lifecycle("fetched " + cfg.resolve().joinToString { it.name })
        }
    }
}

tasks.register("exportOfflineRepo") {
    group = "offline"
    description = "Exports .gradle-offline-home into m2/ and offline/ without running the build again."
    doLast {
        exportDependencyCache()
        exportGradleDistribution()
    }
}

tasks.register("downloadDependencies") {
    group = "offline"
    description = "Downloads every dependency, Gradle plugin, tool and the Gradle distribution into m2/ and offline/ (needs internet once)."
    doLast {
        logger.lifecycle("")
        logger.lifecycle("downloadDependencies: runs the complete build once in $offlineHome and downloads about 450 MB of")
        logger.lifecycle("dependencies (plus the 130 MB Gradle distribution if it is not already cached). Progress is shown below;")
        logger.lifecycle("on a slow connection this can take a long time. Interrupting and re-running continues where it stopped.")
        logger.lifecycle("")
        seedGradleDistribution()
        val serverOnly = providers.gradleProperty("duel2048.serverOnly").isPresent
        val tasksToRun = mutableListOf(":shared:test", ":server:test", ":server:installDist")
        if (!serverOnly) {
            // AGP needs JDK 17 and the Android SDK; skipped for server-only bundles.
            tasksToRun += listOf(":android:assembleDebug", ":android:testDebugUnitTest")
            if (rootDir.resolve("keystore.properties").isFile) tasksToRun += ":android:assembleRelease" else logger.warn("keystore.properties not found: release-only tools are skipped (run tools/create-keystore first to include them)")
        }
        runChildGradle(*tasksToRun.toTypedArray())
        val aapt2Versions = offlineHome.resolve("caches/modules-2/files-2.1/com.android.tools.build/aapt2").listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty()
        for (v in aapt2Versions) runChildGradle("fetchPlatformArtifacts", "-Paapt2Version=$v")
        exportDependencyCache()
        exportGradleDistribution()
        logger.lifecycle("Done. Keep m2/ and offline/ next to the project, then build anywhere with gradlew-offline(.bat).")
    }
}
