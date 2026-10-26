// Every Java module: Java 25 bytecode and JUnit. Plugins add Paper with xena.plugin.
plugins {
    `java-library`
}

val libs = the<VersionCatalogsExtension>().named("libs")

tasks.withType<JavaCompile>().configureEach {
    // Paper 26.x needs Java 25.
    options.release = 25
    options.encoding = "UTF-8"
    // Line numbers for stack traces, without local variable tables: smaller jars.
    options.compilerArgs.add("-g:source,lines")
    options.compilerArgs.add("-Xlint:all,-processing,-serial")
}

dependencies {
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
