plugins {
    id("familyplanner.android-library")
}

android {
    namespace = "de.familyplanner.android.core.testing"
}

dependencies {
    api(project(":core:data"))
    api(libs.coroutines.test)
    api(libs.junit4)
}
