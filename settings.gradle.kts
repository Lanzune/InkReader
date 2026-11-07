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

rootProject.name = "InkReader"
include(":app")
include(":kernel:api")
include(":kernel:core")
include(":reader-engine")
include(":formats:txt")
include(":formats:epub")
include(":formats:pdf")
include(":plugin-host-quickjs")
