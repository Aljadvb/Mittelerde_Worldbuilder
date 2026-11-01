pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MittelerdeWorldbuilder"

include(":model", ":validation", ":engine", ":editing", ":generator", ":editor")

// The Android app needs the Android SDK; skip it with -PskipAndroid=true (e.g. Windows editor build).
if (providers.gradleProperty("skipAndroid").orNull != "true") {
    include(":app")
}
