val kotlinVersion: String by project

plugins {
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.serialization") version "1.6.0"
}

dependencies {
    implementation(project(":core:util"))
    implementation("org.capnproto:runtime:0.1.15")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}
repositories {
    mavenCentral()
}
