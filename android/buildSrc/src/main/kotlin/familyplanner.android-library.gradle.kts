import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("familyplanner.quality")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

extensions.configure<LibraryExtension> {
    compileSdk = 37
    defaultConfig {
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    "testImplementation"(libs.findLibrary("junit4").get())
    "testImplementation"(libs.findLibrary("kotlin-test-junit").get())
    "testImplementation"(libs.findLibrary("coroutines-test").get())
    "testImplementation"(libs.findLibrary("turbine").get())
}

// Robolectric (SDK 36) greift auf JDK-interne Klassen zu.
tasks.withType<Test>().configureEach {
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}
