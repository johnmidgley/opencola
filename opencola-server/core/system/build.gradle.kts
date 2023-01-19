val kotlin_version: String by project
val kotlin_logging_version: String by project
plugins {
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.serialization") version "1.6.0"
}

dependencies {
    implementation(project(":core:io"))
    implementation("io.github.microutils:kotlin-logging:$kotlin_logging_version")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}