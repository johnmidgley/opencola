val kotlinVersion: String by project
val kotlinLoggingVersion:String by project
val kotlinxSerializationVersion: String by project

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
    implementation("org.capnproto:runtime:0.1.15")
    implementation("com.google.protobuf:protobuf-java:3.22.2")


    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:$kotlinxSerializationVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
    testImplementation(project(":test"))
    testImplementation(project(":core:storage"))
}