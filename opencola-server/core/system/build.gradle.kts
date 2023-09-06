val kotlinVersion: String by project
val kotlinLoggingVersion: String by project
plugins {
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.serialization") version "1.6.0"
}

dependencies {
    implementation(project(":core:io"))
    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}