import org.gradle.kotlin.dsl.support.serviceOf
import xyz.jpenilla.resourcefactory.paper.PaperPluginYaml

plugins {
    id("xena.plugin")
}

version = "0.1.0"
description = "The prison break game: teams, arrests, robberies, riots, guns and the rest."

// common's classes go inside the plugin jar. Its libraries don't: Paper downloads
// them on first start (MostWantedLoader), so the jar stays small.
val bundled: Configuration by configurations.creating {
    isTransitive = false
}

// What Paper downloads, from Maven Central's mirror. The same as common's own
// libraries; a missing one fails the boot test with a NoClassDefFoundError.
val paperLibraries: List<String> = listOf<Provider<MinimalExternalModuleDependency>>(libs.jooq.asProvider(), libs.hikari, libs.flyway.core, libs.flyway.postgresql, libs.postgresql)
    .map { it.get() }
    .map { "${it.module}:${it.versionConstraint.requiredVersion}" }

dependencies {
    implementation(project(":common"))
    bundled(project(":common"))
    // The server has FAWE; only its API is needed here.
    compileOnly(libs.fawe.core) { isTransitive = false }
    compileOnly(libs.fawe.bukkit) { isTransitive = false }

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
}

paperPluginYaml {
    name = "MostWanted"
    main = "co.xenastudios.mostwanted.MostWanted"
    loader = "co.xenastudios.mostwanted.MostWantedLoader"
    // Optional: without it the game runs, but zones can't be marked out.
    dependencies {
        server("FastAsyncWorldEdit", PaperPluginYaml.Load.BEFORE, required = false, joinClasspath = true)
    }
}

// The library list MostWantedLoader reads: one group:artifact:version per line.
val writeLibraries by tasks.registering {
    val coordinates = paperLibraries
    val output = layout.buildDirectory.file("generated/libraries/paper-libraries.txt")
    inputs.property("coordinates", coordinates)
    outputs.file(output)
    doLast {
        output.get().asFile.writeText(coordinates.joinToString("\n", postfix = "\n"))
    }
}

tasks.processResources {
    from(writeLibraries)
}

tasks.jar {
    // Locals, so the lambda doesn't capture the build script (the configuration cache can't store it).
    val archives = serviceOf<ArchiveOperations>()
    val jars = bundled.elements
    from(jars.map { files -> files.map { archives.zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF")
    }
}
