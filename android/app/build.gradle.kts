import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("familyplanner.quality")
    id("familyplanner.android-compose")
}

val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use(::load)
    }

fun config(
    property: String,
    env: String,
): String? = localProperties.getProperty(property) ?: System.getenv(env)

val serverUrl = config("fp.serverUrl", "FP_SERVER_URL") ?: "https://planner.home.arpa:8095/"
val signingStoreFile = config("fp.signing.storeFile", "FP_SIGNING_STORE_FILE")

android {
    namespace = "de.familyplanner.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.familyplanner.android"
        minSdk = 29
        targetSdk = 37
        versionCode = 8
        versionName = "0.7.0"
        buildConfigField("String", "FP_SERVER_URL", "\"$serverUrl\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    signingConfigs {
        // Ohne Signatur-Konfiguration bleibt Release unsigniert (CI-Build läuft trotzdem).
        if (signingStoreFile != null) {
            create("release") {
                storeFile = file(signingStoreFile)
                storePassword = config("fp.signing.storePassword", "FP_SIGNING_STORE_PASSWORD")
                keyAlias = config("fp.signing.keyAlias", "FP_SIGNING_KEY_ALIAS")
                keyPassword = config("fp.signing.keyPassword", "FP_SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:meals"))
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.core.ktx)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.coroutines.test)
}

tasks.withType<Test>().configureEach {
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}

dependencies {
    kover(project(":core:data"))
    kover(project(":core:ui"))
    kover(project(":feature:auth"))
    kover(project(":feature:meals"))
}

kover {
    reports {
        filters {
            excludes {
                // Generierter Client, Compose-Boilerplate, Koin-Verdrahtung und Einstiegspunkte (ADR-0009).
                packages("de.familyplanner.api")
                classes(
                    "*.BuildConfig",
                    "*ComposableSingletons*",
                    "*.R",
                    "*.R$*",
                    "*Module*",
                    "*.MainActivity",
                    "*.FamilyPlannerApp",
                    "*.WebViewPageScriptLoader*", // WebView: nur auf dem Gerät prüfbar (ADR-0012)
                    // Android-Einstiegspunkte (Alarm, Neustart, WorkManager): nur auf dem Gerät prüfbar (ADR-0013)
                    "*Receiver*",
                    "*ReminderSyncWorker*",
                    "*WorkManagerReminderSync*",
                    "*.theme.*",
                    "*Route*",
                )
                annotatedBy("androidx.compose.ui.tooling.preview.Preview")
            }
        }
        verify {
            rule {
                minBound(80)
            }
        }
    }
}
