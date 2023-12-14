import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.archivesName

val ktorVersion: String by project
val kotlinVersion: String by project
val logbackVersion: String by project
val kotlinLoggingVersion:String by project
val bcprovVersion: String by project
val exposedVersion: String by project
val postgresqlVersion: String by project
val kotlinxCliVersion: String by project
val hopliteVersion: String by project

plugins {
    application
    kotlin("jvm") version "1.9.0"
}

archivesName.set("relay-server")

group = "io.opencola"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("io.opencola.relay.server.RelayApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    implementation(project(":core:event"))
    implementation(project(":core:security"))
    implementation(project(":core:serialization"))
    implementation(project(":core:io"))
    implementation(project(":core:model"))
    implementation(project(":core:storage"))
    implementation(project(":core:util"))
    implementation(project(":relay:common"))
    implementation(project(":relay:client"))
    implementation("com.sksamuel.hoplite:hoplite-core:$hopliteVersion")
    implementation("com.sksamuel.hoplite:hoplite-yaml:$hopliteVersion")
    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
    implementation("ch.qos.logback:logback-core:$logbackVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.bouncycastle:bcprov-jdk15on:$bcprovVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-cli:$kotlinxCliVersion")
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-websockets-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")

    testImplementation(project(":core:util"))
    testImplementation(project(":core:io"))
    testImplementation(project(":core:storage"))
    testImplementation(project(":test"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
}