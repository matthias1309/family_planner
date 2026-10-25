plugins {
    id("familyplanner.kotlin-jvm")
    alias(libs.plugins.ktor)
}

application {
    mainClass.set("de.familyplanner.app.ApplicationKt")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":meals"))
    implementation(project(":tasks"))
    implementation(project(":gamification"))
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.call.id)
    implementation(libs.koin.ktor)
    implementation(libs.koin.slf4j)
    implementation(libs.logback)

    kover(project(":core"))
    kover(project(":meals"))
    kover(project(":tasks"))
    kover(project(":gamification"))

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.swagger.validator)
}

tasks.test {
    systemProperty("openapi.spec", rootDir.resolve("../api/openapi.yaml").absolutePath)
}

kover {
    reports {
        filters {
            excludes {
                packages("de.familyplanner.api.model")
                classes("de.familyplanner.app.ApplicationKt")
            }
        }
        verify {
            rule {
                minBound(80)
            }
        }
    }
}
