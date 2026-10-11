rootProject.name = "family-planner-backend"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":api-contract", ":core", ":app")
