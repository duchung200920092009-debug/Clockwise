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

rootProject.name = "ZenPulse"

// :app is the Wear OS app (the product). :mobile is the phone companion that displays the
// episode history synced over the Wearable Data Layer.
include(":app")
include(":mobile")
