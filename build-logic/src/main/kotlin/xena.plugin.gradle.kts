// A server plugin: compiled against Paper, with a generated paper-plugin.yml and a dev server (runServer).
plugins {
    id("xena.java")
    id("xyz.jpenilla.resource-factory-paper-convention")
    id("xyz.jpenilla.run-paper")
}

val libs = the<VersionCatalogsExtension>().named("libs")

// "26.3.build.223-stable" -> "26.3"
val minecraftVersion = libs.findVersion("paper").get().requiredVersion.substringBefore(".build")

// -Pxena.newestPaper=<paper-api version> compiles against a newer Paper instead,
// failing on API it marks for removal. CI does this; the jars it makes aren't released.
val newestPaper = providers.gradleProperty("xena.newestPaper")

if (newestPaper.isPresent) {
    tasks.withType<JavaCompile>().configureEach {
        // Only removals matter for this check, and they fail it.
        options.compilerArgs.removeAll { it.startsWith("-Xlint") }
        options.compilerArgs.addAll(listOf("-Xlint:removal", "-Werror"))
    }
}

dependencies {
    if (newestPaper.isPresent) {
        compileOnly("io.papermc.paper:paper-api:${newestPaper.get()}")
    } else {
        compileOnly(libs.findLibrary("paper-api").get())
    }
    testImplementation(libs.findLibrary("paper-api").get())
}

paperPluginYaml {
    apiVersion = minecraftVersion
    authors.add("Xena Studios")
    website = "https://github.com/xena-studios/mostwanted"
}

tasks.runServer {
    minecraftVersion(minecraftVersion)
}
