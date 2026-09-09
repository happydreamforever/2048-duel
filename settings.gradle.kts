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
include(":shared", ":server", ":android")
