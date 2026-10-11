import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
    id("org.jetbrains.kotlinx.kover")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testRuntimeOnly(libs.findLibrary("junit-launcher").get())
    testImplementation(libs.findLibrary("kotlin-test-junit5").get())
    testImplementation(libs.findLibrary("coroutines-test").get())
}

tasks.test {
    useJUnitPlatform()
}

configure<DetektExtension> {
    buildUponDefaultConfig = true
    config.setFrom(rootDir.resolve("config/detekt/detekt.yml"))
}
