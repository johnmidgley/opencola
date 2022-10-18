val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val exposed_version: String by project
val kotlinx_serialization_version: String by project
val kotlin_logging_version:String by project
val slf4j_version: String by project
val mime4j_version: String by project
val bcprov_version: String by project
val hoplite_version: String by project
val kodein_version: String by project
val kotlinx_cli_version: String by project

/*

DOCS
********************************************************
https://docs.gradle.org/current/userguide/userguide.html
********************************************************

*/

plugins {
    application
    kotlin("jvm") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.0"
    // id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "opencola"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("opencola.server.ApplicationKt")
}

repositories {
    mavenCentral()
    flatDir{
        name = "localRepository"
        dirs("${project.rootDir}/../lib")
    }
}

// https://thelyfsoshort.io/kotlin-reflection-shadow-jars-minimize-9bd74964c74
// NOTE: This does not work, likely due to some reflection magic that is invisible
// to the minimizer
//tasks.shadowJar {
//    minimize {
//        // exclude(dependency("org.jetbrains.kotlin:.*"))
//    }
//}

dependencies {
    implementation(project(":core"))

    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinx_serialization_version")

    // Logging: https://www.kotlinresources.com/library/kotlin-logging/
    implementation("io.github.microutils:kotlin-logging:$kotlin_logging_version")
    implementation("org.slf4j:slf4j-api:$slf4j_version")
    implementation("org.apache.james:apache-mime4j-core:$mime4j_version")
    implementation("org.apache.james:apache-mime4j-dom:$mime4j_version")

    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("ch.qos.logback:logback-core:$logback_version")
    implementation("org.bouncycastle:bcprov-jdk15on:$bcprov_version")
    implementation("org.kodein.di:kodein-di:$kodein_version")
    implementation("org.kodein.di:kodein-di-framework-ktor-server-jvm:$kodein_version")
    implementation("org.jetbrains.kotlinx:kotlinx-cli:$kotlinx_cli_version")

    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-network-tls-certificates-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-status-pages:$ktor_version")
    implementation("io.ktor:ktor-server-cors:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("io.ktor:ktor-server-html-builder:$ktor_version")
    implementation("io.ktor:ktor-server-sessions:$ktor_version")
    implementation("io.ktor:ktor-client-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-cio-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-serialization-jvm:$ktor_version")

    testImplementation(project(":relay"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktor_version")
}