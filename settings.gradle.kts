pluginManagement {
    repositories {
        // Project-local Maven repository written by `gradlew downloadDependencies` (offline builds).
        // Listed first so nothing is fetched from the internet when it is present.
        val offlineRepo = File(rootDir, "offline-repo")
        if (offlineRepo.isDirectory && System.getProperty("duel2048.offlineRepo") != "false") {
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
        if (offlineRepo.isDirectory && System.getProperty("duel2048.offlineRepo") != "false") {
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
