pluginManagement {
    plugins {
        id("com.google.devtools.ksp") version "1.6.10-1.0.2"
        kotlin("jvm") version "1.6.10"
    }
    repositories {
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "jlox"
include("ASTBuilder")
include("main")
