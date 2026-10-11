import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension

plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

pluginManager.withPlugin("com.android.library") {
    extensions.configure<LibraryExtension> { buildFeatures.compose = true }
}
pluginManager.withPlugin("com.android.application") {
    extensions.configure<ApplicationExtension> { buildFeatures.compose = true }
}

dependencies {
    val bom = platform(libs.findLibrary("compose-bom").get())
    "implementation"(bom)
    "implementation"(libs.findLibrary("compose-ui").get())
    "implementation"(libs.findLibrary("compose-material3").get())
    "implementation"(libs.findLibrary("compose-ui-tooling-preview").get())
    "debugImplementation"(libs.findLibrary("compose-ui-tooling").get())
    "debugImplementation"(libs.findLibrary("compose-ui-test-manifest").get())
    "testImplementation"(bom)
    "testImplementation"(libs.findLibrary("compose-ui-test-junit4").get())
    "testImplementation"(libs.findLibrary("robolectric").get())
}
