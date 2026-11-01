plugins {
    id("familyplanner.kotlin-jvm")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.koin.ktor)
    implementation(libs.logback)

    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.serialization.json)
}

tasks.test {
    systemProperty("avatar.definition", rootDir.resolve("../api/avatar/avataaars.json").absolutePath)
}
