plugins {
    id("xena.java")
    application
    alias(libs.plugins.shadow)
}

description = "The website's API and the Discord bot, in one app."

dependencies {
    implementation(project(":common"))
    implementation(libs.javalin)
    implementation(libs.jackson.databind)
    implementation(libs.jda) {
        // Voice: the bot never joins a call.
        exclude(module = "opus-java")
        exclude(module = "tink")
    }
    implementation(libs.bucket4j)
    implementation(libs.logback)

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
}

application {
    mainClass = "co.xenastudios.mostwanted.backend.Backend"
    // 256 MB with the Serial GC: the lightest setup for a small heap (docs/04-tech.md).
    applicationDefaultJvmArgs = listOf("-Xmx256m", "-XX:+UseSerialGC")
}

// The jar without its libraries, renamed so it doesn't clash with the runnable one.
tasks.jar {
    archiveClassifier = "plain"
}

tasks.shadowJar {
    // One runnable jar for Raptor: backend/build/libs/backend.jar.
    archiveFileName = "backend.jar"
    // Flyway, Jetty and JDA find their parts through service files: merge them, don't
    // overwrite. Shadow drops duplicates before its transformers see them unless they're
    // let through; the transformers then write each merged file once.
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
