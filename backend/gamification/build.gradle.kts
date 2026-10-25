plugins {
    id("familyplanner.kotlin-jvm")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.koin.ktor)
    implementation(libs.logback)

    testImplementation(testFixtures(project(":core")))
}
