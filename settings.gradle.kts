pluginManagement {
    repositories {
        // Project-local Maven repository written by `gradlew downloadDependencies` (offline builds).
        // Listed first so nothing is fetched from the internet when it is present.
        // Skipped while (re)generating it: on Windows a build that loaded jars from offline-repo
        // would keep them locked and the export could not overwrite them.
        val offlineRepo = File(rootDir, "offline-repo")
        val exporting = gradle.startParameter.taskNames.any { it.endsWith("downloadDependencies") || it.endsWith("exportOfflineRepo") }
        if (offlineRepo.isDirectory && !exporting && System.getProperty("duel2048.offlineRepo") != "false") {
            maven {
                name = "offlineRepo"
                url = offlineRepo.toURI()
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
        val offlineRepo = File(rootDir, "offline-repo")
        val exporting = gradle.startParameter.taskNames.any { it.endsWith("downloadDependencies") || it.endsWith("exportOfflineRepo") }
        if (offlineRepo.isDirectory && !exporting && System.getProperty("duel2048.offlineRepo") != "false") {
            maven {
                name = "offlineRepo"
                url = offlineRepo.toURI()
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
if (!serverOnly) include(":android")
