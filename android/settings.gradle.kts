rootProject.name = "family-planner-android"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":app", ":core:api", ":core:data", ":core:ui", ":core:testing", ":feature:auth", ":feature:meals")
