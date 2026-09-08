// Root build file. Module builds live in shared/, server/ and android/.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// ---------------------------------------------------------------------------------------------
// Offline builds.
//
//   gradlew downloadDependencies   (once, with internet)  -> offline-repo/ + offline/gradle-x.y.z/
//   gradlew-offline(.bat) <tasks>  (anywhere, no internet)
//
// downloadDependencies runs the complete build in a fresh, project-local Gradle home so that
// exactly this project's artifacts (dependencies, Gradle plugins, Kotlin compiler, AGP tools,
// aapt2 for Windows/macOS/Linux) are fetched, then exports that cache as a Maven-layout
// repository which settings.gradle.kts puts first in the repository list.
// ---------------------------------------------------------------------------------------------

val offlineHome: File = rootDir.resolve(".gradle-offline-home")
val offlineRepo: File = rootDir.resolve("offline-repo")
val offlineDistDir: File = rootDir.resolve("offline")

fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")

fun runChildGradle(vararg args: String) {
    val launcher = if (isWindows()) listOf("cmd", "/c", rootDir.resolve("gradlew.bat").absolutePath) else listOf(rootDir.resolve("gradlew").absolutePath)
    val command = launcher + listOf("--no-daemon", "-Dduel2048.offlineRepo=false") + args
    logger.lifecycle("> child build: ${args.joinToString(" ")}  (GRADLE_USER_HOME=$offlineHome)")
    val process = ProcessBuilder(command).directory(rootDir).inheritIO().also { it.environment()["GRADLE_USER_HOME"] = offlineHome.absolutePath }.start()
    val code = process.waitFor()
    if (code != 0) throw GradleException("child build failed with exit code $code: ${args.joinToString(" ")}")
}

/**
 * Gradle Module Metadata lists each artifact with a logical `name` (what the dependency cache
 * stores, e.g. `material3-release.aar`) and a repository `url` (what a Maven repository must be
 * called, e.g. `material3-android-1.3.1.aar`). Returns name -> url for one version directory.
 */
fun artifactRenames(versionDir: File): Map<String, String> {
    val moduleFile = versionDir.walkTopDown().maxDepth(2).firstOrNull { it.isFile && it.name.endsWith(".module") } ?: return emptyMap()
    val renames = HashMap<String, String>()
    try {
        @Suppress("UNCHECKED_CAST")
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

/** files-2.1/<group>/<module>/<version>/<sha1>/<file>  ->  offline-repo/<group/as/path>/<module>/<version>/<url-name> */
fun exportDependencyCache() {
    val files21 = offlineHome.resolve("caches/modules-2/files-2.1")
    require(files21.isDirectory) { "no dependency cache at $files21; run the child build first" }
    offlineRepo.deleteRecursively()
    var count = 0
    var bytes = 0L
    var renamed = 0
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
                        target.parentFile.mkdirs()
                        file.copyTo(target, overwrite = true)
                        count++
                        bytes += file.length()
                    }
                }
            }
        }
    }
    logger.lifecycle("offline-repo: $count files, ${bytes / 1_048_576} MB ($renamed renamed per module metadata)")
}

fun exportGradleDistribution() {
    val dists = offlineHome.resolve("wrapper/dists")
    val dist = dists.walkTopDown().maxDepth(3).firstOrNull { it.isDirectory && it.name.startsWith("gradle-") && it.resolve("bin").isDirectory }
        ?: throw GradleException("no Gradle distribution under $dists")
    val target = offlineDistDir.resolve(dist.name)
    target.deleteRecursively()
    dist.copyRecursively(target, overwrite = true)
    target.resolve("bin/gradle").setExecutable(true)
    logger.lifecycle("offline/${dist.name}: Gradle distribution copied")
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
    description = "Exports .gradle-offline-home into offline-repo/ and offline/ without running the build again."
    doLast {
        exportDependencyCache()
        exportGradleDistribution()
    }
}

tasks.register("downloadDependencies") {
    group = "offline"
    description = "Downloads every dependency, Gradle plugin, tool and the Gradle distribution into offline-repo/ and offline/ (needs internet once)."
    doLast {
        val tasksToRun = mutableListOf(":shared:test", ":server:test", ":server:installDist", ":android:assembleDebug", ":android:testDebugUnitTest")
        if (rootDir.resolve("keystore.properties").isFile) tasksToRun += ":android:assembleRelease" else logger.warn("keystore.properties not found: release-only tools are skipped (run tools/create-keystore first to include them)")
        runChildGradle(*tasksToRun.toTypedArray())
        val aapt2Versions = offlineHome.resolve("caches/modules-2/files-2.1/com.android.tools.build/aapt2").listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty()
        for (v in aapt2Versions) runChildGradle("fetchPlatformArtifacts", "-Paapt2Version=$v")
        exportDependencyCache()
        exportGradleDistribution()
        logger.lifecycle("Done. Commit offline-repo/ and offline/, then build anywhere with gradlew-offline(.bat).")
    }
}
