plugins {
    id("familyplanner.kotlin-jvm")
    `java-test-fixtures`
}

dependencies {
    api(project(":api-contract"))
    api(libs.ktor.server.core)
    api(libs.ktor.server.auth)
    api(libs.ktor.server.rate.limit)
    api(libs.exposed.core)
    api(libs.exposed.jdbc)
    api(libs.coroutines.core)
    implementation(libs.sqlite.jdbc)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.koin.ktor)
    implementation(libs.logback)
}
