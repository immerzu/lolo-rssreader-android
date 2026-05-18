pluginManagement {
    repositories {
        maven(url = "https://maven.google.com")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven(url = "https://maven.google.com")
        mavenCentral()
    }
}

rootProject.name = "RSSReader"
include(":app")


