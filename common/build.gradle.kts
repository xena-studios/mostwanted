plugins {
    id("xena.java")
    alias(libs.plugins.jooq.codegen)
}

description = "The database tables, migrations and message style, shared by core and backend."

dependencies {
    // jOOQ's query types show in common's API; the rest stay inside it.
    api(libs.jooq)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.postgresql)
    // Paper and backend both provide the logging behind it.
    implementation(libs.slf4j.api)

    // Only so javac can read the annotations on jOOQ's settings classes; never needed at run time.
    compileOnly(libs.jakarta.xml.bind)

    // Reads the migrations to generate the table classes, without a database.
    jooqCodegen(libs.jooq.meta.extensions)

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.slf4j.simple)
}

// The table classes come from the migrations themselves, so the Java can't
// drift from the schema: change a column and every use of it stops compiling.
// Indexes jOOQ can't parse sit between [jooq ignore] markers in the SQL.
jooq {
    configuration {
        generator {
            database {
                name = "org.jooq.meta.extensions.ddl.DDLDatabase"
                inputSchema = "mostwanted"
                properties {
                    property {
                        key = "scripts"
                        value = "src/main/resources/db/migration/*.sql"
                    }
                    property {
                        key = "sort"
                        value = "flyway"
                    }
                    property {
                        key = "parseIgnoreComments"
                        value = "true"
                    }
                    property {
                        key = "defaultNameCase"
                        value = "lower"
                    }
                }
                // jOOQ reads the SQL by simulating it in H2, which turns JSONB into JSON.
                // We only use JSONB. The database tests run these classes against a real
                // PostgreSQL, so any other mismatch fails there.
                forcedTypes {
                    forcedType {
                        name = "JSONB"
                        includeTypes = "JSON"
                    }
                }
            }
            generate {
                isDeprecated = false
                isRecords = true
                isFluentSetters = true
                isJavaTimeTypes = true
            }
            target {
                packageName = "co.xenastudios.mostwanted.common.db"
            }
        }
    }
}

tasks.named("jooqCodegen") {
    inputs.files(fileTree("src/main/resources/db/migration"))
}

tasks.compileJava {
    dependsOn(tasks.named("jooqCodegen"))
}
