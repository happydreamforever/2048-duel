pluginManagement {
    repositories {
        // Project-local Maven repository (m2/) written by `gradlew downloadDependencies`.
        // Listed first so nothing is fetched from the internet when it is present.
        // Skipped while (re)generating it: on Windows a build that loaded jars from m2
        // would keep them locked and the export could not overwrite them.
        val m2 = File(rootDir, "m2")
        val exporting = gradle.startParameter.taskNames.any { it.endsWith("downloadDependencies") || it.endsWith("exportOfflineRepo") }
        val disabled = System.getProperty("duel2048.m2") == "false" || System.getProperty("duel2048.offlineRepo") == "false"
        if (m2.isDirectory && !exporting && !disabled) {
            maven {
                name = "m2"
                url = m2.toURI()
            }
        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val m2 = File(rootDir, "m2")
        val exporting = gradle.startParameter.taskNames.any { it.endsWith("downloadDependencies") || it.endsWith("exportOfflineRepo") }
        val disabled = System.getProperty("duel2048.m2") == "false" || System.getProperty("duel2048.offlineRepo") == "false"
        if (m2.isDirectory && !exporting && !disabled) {
            maven {
                name = "m2"
                url = m2.toURI()
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "duel2048"

// -Pduel2048.serverOnly excludes the Android module so :shared/:server can be built on hosts
// without the Android SDK or a JDK new enough for AGP 8 (which requires JDK 17): Gradle 8 itself
// runs on JDK 8. Server-only offline bundles (gradlew downloadDependencies -Pduel2048.serverOnly)
// do not contain AGP, so such hosts MUST set this property.
val serverOnly = providers.gradleProperty("duel2048.serverOnly").isPresent
if (serverOnly) logger.lifecycle("duel2048.serverOnly: :android is excluded from this build")
include(":shared", ":server")
if (!serverOnly) {
    include(":android")
    include(":cube2")
}
