@file:Suppress("UnstableApiUsage")

rootProject.name = "GuitarPan"
include(":app")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // THIS BLOCK IS CRUCIAL FOR RESOLVING LIBRARY DEPENDENCIES
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS) // Good practice
    repositories {
        google()       // For AndroidX libraries, Google Play Services, etc.
        mavenCentral() // For Kotlin stdlib, and many other common libraries
        // You might have other repositories here if needed, e.g., jcenter() (deprecated) or custom ones
    }
}
