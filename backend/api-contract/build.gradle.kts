plugins {
    id("familyplanner.kotlin-jvm")
    alias(libs.plugins.openapi.generator)
}

val generatedDir = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$rootDir/../api/openapi.yaml")
    outputDir.set(generatedDir.map { it.asFile.absolutePath })
    packageName.set("de.familyplanner.api")
    modelPackage.set("de.familyplanner.api.model")
    globalProperties.set(
        mapOf("models" to "", "modelDocs" to "false", "modelTests" to "false"),
    )
    typeMappings.set(mapOf("UUID" to "kotlin.String"))
    configOptions.set(
        mapOf(
            "serializationLibrary" to "kotlinx_serialization",
            "dateLibrary" to "string",
            "enumPropertyNaming" to "UPPERCASE",
        ),
    )
}

sourceSets.main {
    kotlin.srcDir(generatedDir.map { it.dir("src/main/kotlin") })
}

dependencies {
    api(libs.serialization.json)
}

tasks.named("compileKotlin") { dependsOn("openApiGenerate") }
tasks.matching { it.name.startsWith("runKtlint") }.configureEach { dependsOn("openApiGenerate") }

ktlint {
    filter {
        exclude { it.file.path.contains("generated") }
    }
}

detekt {
    source.setFrom(emptyList<File>())
}
