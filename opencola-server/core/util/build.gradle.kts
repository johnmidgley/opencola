val kotlinVersion: String by project
val kotlinxSerializationVersion: String by project
val protobufVersion: String by project

plugins {
    kotlin("jvm") version "1.9.0"
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}