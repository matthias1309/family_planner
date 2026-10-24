plugins {
    id("familyplanner.android-library")
    id("familyplanner.android-compose")
}

android {
    namespace = "de.familyplanner.android.feature.meals"
}

dependencies {
    api(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.koin.compose)
    implementation(libs.datastore)
    implementation(libs.work.runtime)
    implementation(libs.core.ktx)
    implementation(libs.navigation.compose)
    implementation(libs.compose.material.icons)

    testImplementation(project(":core:testing"))
    testImplementation(libs.mockwebserver)
}
