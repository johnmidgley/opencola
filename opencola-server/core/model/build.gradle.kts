val kotlin_version: String by project
val kotlin_logging_version:String by project
val kotlinx_serialization_version: String by project

plugins {
    // TODO: These should be specified at top level (or at least the versions)
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.serialization") version "1.6.0"
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":core:util"))
    implementation(project(":core:security"))
    implementation(project(":core:serialization"))

    implementation("io.github.microutils:kotlin-logging:$kotlin_logging_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:$kotlinx_serialization_version")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}