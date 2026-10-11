plugins {
    id("familyplanner.android-library")
    alias(libs.plugins.openapi.generator)
}

val generatedDir = layout.buildDirectory.dir("generated/openapi")

android {
    namespace = "de.familyplanner.android.core.api"
}

androidComponents.onVariants { variant ->
    variant.sources.kotlin?.addStaticSourceDirectory(
        generatedDir
            .map { it.dir("src/main/kotlin") }
            .get()
            .asFile.path,
    )
}

openApiGenerate {
    generatorName.set("kotlin")
    library.set("jvm-retrofit2")
    inputSpec.set("$rootDir/../api/openapi.yaml")
    outputDir.set(generatedDir.map { it.asFile.absolutePath })
    packageName.set("de.familyplanner.api")
    apiPackage.set("de.familyplanner.api.client")
    modelPackage.set("de.familyplanner.api.model")
    globalProperties.set(
        mapOf(
            "apis" to "",
            "models" to "",
            "modelDocs" to "false",
            "modelTests" to "false",
            "apiDocs" to "false",
            "apiTests" to "false",
            "supportingFiles" to "CollectionFormats.kt",
        ),
    )
    typeMappings.set(mapOf("UUID" to "kotlin.String"))
    configOptions.set(
        mapOf(
            "serializationLibrary" to "kotlinx_serialization",
            "useCoroutines" to "true",
            "dateLibrary" to "string",
            "enumPropertyNaming" to "UPPERCASE",
        ),
    )
}

dependencies {
    api(libs.serialization.json)
    api(libs.retrofit)
    api(libs.retrofit.serialization)
    api(libs.okhttp)
}

// Die generierten Quellen werden von vielen Tasks gelesen (Compile, Lint, Annotationen, Kover …);
// deshalb hängt jeder Task außer dem Generator und „clean“ vom Generator ab.
tasks.configureEach {
    if (name != "openApiGenerate" && name != "clean") dependsOn("openApiGenerate")
}

ktlint {
    filter {
        exclude { it.file.path.contains("generated") }
    }
}

detekt {
    source.setFrom(emptyList<File>())
}
