import nu.studer.gradle.jooq.JooqEdition

plugins {
    kotlin("jvm") version "1.8.0"
    application

    id("nu.studer.jooq") version "8.1"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation("org.postgresql:postgresql:42.2.27")
    implementation("org.jooq:jooq:3.17.6")
    jooqGenerator("org.postgresql:postgresql:42.2.27")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("MainKt")
}

jooq {
    version.set("3.17.6")
    edition.set(JooqEdition.OSS)

    configurations {
        create("main") {
            jooqConfiguration.apply {
                logging = org.jooq.meta.jaxb.Logging.DEBUG

                jdbc.apply {
                    driver = "org.postgresql.Driver"
                    url = "jdbc:postgresql://localhost:5432/byos"
                    user = "postgres"
                    password = ""
                }
                generator.apply {
                    name = "org.jooq.codegen.JavaGenerator"
                    database.apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = "public"
                        includes = ".*"
                        excludes = ""
                    }
                    target.apply {
                        packageName = "db.jooq.generated"
                        directory = "${project.projectDir}/src/generated/java/jooq"
                    }
                }
            }
        }
    }
}
