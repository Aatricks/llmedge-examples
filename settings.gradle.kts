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

includeBuild("..") {
    dependencySubstitution {
        substitute(module("io.aatricks:llmedge")).using(project(":llmedge"))
    }
}

rootProject.name = "llmedge-examples"
include(":app")
