val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val kotlin_logging_version:String by project
val bcprov_version: String by project

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
    implementation(project(":core"))
    implementation(project(":security"))

    // Logging
    implementation("io.github.microutils:kotlin-logging:$kotlin_logging_version")
    implementation("ch.qos.logback:logback-core:$logback_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")

    // Security
    implementation("org.bouncycastle:bcprov-jdk15on:$bcprov_version")

    // Ktor
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-websockets-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-cio-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-websockets-jvm:$ktor_version")

    testImplementation(project(":util"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktor_version")
}