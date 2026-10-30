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

rootProject.name = "nuvora"

include(":app")
include(":core:designsystem")
include(":core:data")
include(":core:player")
include(":source:api")
include(":feature:mobile")
include(":feature:tv")
include(":feature:sources")
