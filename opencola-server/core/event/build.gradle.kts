val kotlin_version: String by project
val kotlin_logging_version:String by project
val exposed_version: String by project
val sqlite_version: String by project

plugins {
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.serialization") version "1.6.0"
}

dependencies {
    implementation(project(":core:util"))

    implementation("io.github.microutils:kotlin-logging:$kotlin_logging_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.xerial:sqlite-jdbc:$sqlite_version")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}