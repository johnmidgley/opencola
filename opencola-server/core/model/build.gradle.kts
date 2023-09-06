val kotlinVersion: String by project
val kotlinLoggingVersion:String by project
val kotlinxSerializationVersion: String by project
val protobufVersion: String by project

plugins {
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.serialization") version "1.6.0"
}

dependencies {
    implementation(project(":core:util"))
    implementation(project(":core:security"))
    implementation(project(":core:serialization"))
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:$kotlinxSerializationVersion")

    testImplementation(project(":test"))
    testImplementation(project(":core:storage"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}