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

rootProject.name = "BRTCSampleApp"
include(":app")
include(":bandwidthrtc")
project(":bandwidthrtc").projectDir = file("../kotlin-brtc-sdk/bandwidthrtc")
