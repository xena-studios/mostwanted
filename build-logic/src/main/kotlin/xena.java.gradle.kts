// Every module: Java 25 bytecode, compiled against the Paper the server runs.
plugins {
    `java-library`
}

val libs = the<VersionCatalogsExtension>().named("libs")

// -Pxena.newestPaper=<paper-api version> compiles against a newer Paper instead,
// failing on API it marks for removal. CI does this; the jars it makes aren't released.
val newestPaper = providers.gradleProperty("xena.newestPaper")

tasks.withType<JavaCompile>().configureEach {
    // Paper 26.x needs Java 25.
    options.release = 25
    options.encoding = "UTF-8"
    // Line numbers for stack traces, without local variable tables: smaller jars.
    options.compilerArgs.add("-g:source,lines")
    options.compilerArgs.addAll(newestPaper.map { listOf("-Xlint:removal", "-Werror") }
        .orElse(listOf("-Xlint:all,-processing,-serial")).get())
}

dependencies {
    if (newestPaper.isPresent) {
        compileOnly("io.papermc.paper:paper-api:${newestPaper.get()}")
    } else {
        compileOnly(libs.findLibrary("paper-api").get())
    }

    testImplementation(libs.findLibrary("paper-api").get())
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testRuntimeOnly(libs.findLibrary("junit-launcher").get())
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
