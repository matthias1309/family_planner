plugins {
    id("familyplanner.android-library")
    id("familyplanner.android-compose")
}

android {
    namespace = "de.familyplanner.android.core.ui"
}

dependencies {
    api(project(":core:data"))
    implementation(libs.lifecycle.runtime.compose)
}
