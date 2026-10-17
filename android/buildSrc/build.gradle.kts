plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.plugin.agp)
    implementation(libs.plugin.kotlin)
    implementation(libs.plugin.kotlin.serialization)
    implementation(libs.plugin.kotlin.compose)
    implementation(libs.plugin.ktlint)
    implementation(libs.plugin.detekt)
    implementation(libs.plugin.kover)
}
