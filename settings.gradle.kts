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
        // Bibliothèque DantSu ESCPOS-ThermalPrinter-Android (impression thermique Bluetooth)
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "prestaflow-android"
include(":app")
