val ktorVersion: String by project
val kotlinVersion: String by project
val logbackVersion: String by project
val kotlinLoggingVersion:String by project
val bcprovVersion: String by project

plugins {
    application
    kotlin("jvm") version "1.7.20"
}

group = "io.opencola"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("io.opencola.relay.server.RelayApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    flatDir{
        name = "localRepository"
        dirs("${project.rootDir}/../lib")
    }
}

dependencies {
    implementation(project(":core:security"))
    implementation(project(":core:serialization"))
    implementation(project(":core:model"))
    implementation(project(":relay:common"))
    implementation(project(":relay:client"))

    // Logging
    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
    implementation("ch.qos.logback:logback-core:$logbackVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    // Security
    implementation("org.bouncycastle:bcprov-jdk15on:$bcprovVersion")

    // Ktor
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-websockets-jvm:$ktorVersion")

    testImplementation(project(":core:util"))
    testImplementation(project(":core:io"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
}