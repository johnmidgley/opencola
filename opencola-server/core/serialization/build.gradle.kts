val kotlinVersion: String by project
val protobufVersion: String by project

plugins {
    kotlin("jvm") version "1.7.20"
}

dependencies {
    implementation(project(":core:util"))
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}
