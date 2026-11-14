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
rootProject.name = "habitrpg"
include(
    ":core:domain",
    ":core:data",
    ":feature:tasks",
    ":feature:character",
    ":feature:rewards",
    ":feature:settings",
    ":app",
)
