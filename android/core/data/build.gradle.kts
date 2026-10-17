plugins {
    id("familyplanner.android-library")
}

android {
    namespace = "de.familyplanner.android.core.data"
}

dependencies {
    api(project(":core:api"))
    api(libs.coroutines.core)
    api(libs.serialization.json)
    implementation(libs.coroutines.android)
    implementation(libs.datastore)
    implementation(libs.tink)
    api(libs.koin.android)

    testImplementation(project(":core:testing"))
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
}
