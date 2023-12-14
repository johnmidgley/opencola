val kotlinVersion: String by project
val protobufVersion: String by project
val kotlinxSerializationVersion: String by project

plugins {
    kotlin("jvm") version "1.9.0"
}

dependencies {
    implementation(project(":core:util"))
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:$kotlinxSerializationVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}
