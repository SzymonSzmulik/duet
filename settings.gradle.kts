pluginManagement {
    repositories {
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
        google()
        mavenCentral()
        // NewPipe Extractor is only published on JitPack.
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\.TeamNewPipe.*") }
        }
    }
}

rootProject.name = "Duet"
include(":shared")
// The Android app needs the Android SDK; `-PdesktopOnly` leaves it out (see docs/DESKTOP.md).
if (!providers.gradleProperty("desktopOnly").isPresent) include(":androidApp")
include(":desktopApp")
