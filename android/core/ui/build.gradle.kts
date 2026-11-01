plugins {
    id("familyplanner.android-library")
    id("familyplanner.android-compose")
}

android {
    namespace = "de.familyplanner.android.core.ui"
}

// Avatar-Definition liegt als gemeinsamer Vertrag in api/avatar (ADR-0017) und wird als Asset eingebunden.
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addStaticSourceDirectory(rootDir.resolve("../api/avatar").path)
    }
}

dependencies {
    api(project(":core:data"))
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.androidsvg)
    implementation(libs.serialization.json)
}
