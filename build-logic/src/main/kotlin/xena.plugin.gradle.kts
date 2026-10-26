// A server plugin: a generated paper-plugin.yml and a dev server (runServer).
plugins {
    id("xena.java")
    id("xyz.jpenilla.resource-factory-paper-convention")
    id("xyz.jpenilla.run-paper")
}

val libs = the<VersionCatalogsExtension>().named("libs")

// "26.3.build.223-stable" -> "26.3"
val minecraftVersion = libs.findVersion("paper").get().requiredVersion.substringBefore(".build")

paperPluginYaml {
    apiVersion = minecraftVersion
    authors.add("Xena Studios")
    website = "https://github.com/xena-studios/mostwanted"
}

tasks.runServer {
    minecraftVersion(minecraftVersion)
}
