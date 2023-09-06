val kotlinVersion: String by project
val kotlinLoggingVersion:String by project
val exposedVersion: String by project
val sqliteVersion: String by project

plugins {
    kotlin("jvm") version "1.7.20"
}

dependencies {
    implementation(project(":core:util"))
    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.xerial:sqlite-jdbc:$sqliteVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}