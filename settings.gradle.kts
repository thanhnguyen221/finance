pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // (không bắt buộc, nhưng an toàn thêm luôn)
        maven("https://jitpack.io")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") // cần cho MPAndroidChart
    }
}

rootProject.name = "finance"
include(":app")
