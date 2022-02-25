val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project

plugins {
    application
    kotlin("jvm") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "opencola"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("opencola.server.ApplicationKt")
}

repositories {
    mavenCentral()
}

// https://thelyfsoshort.io/kotlin-reflection-shadow-jars-minimize-9bd74964c74
tasks.shadowJar {
    minimize {
        exclude(dependency("org.jetbrains.kotlin:.*"))
    }
}

dependencies {
    implementation("org.jetbrains.exposed", "exposed-core", "0.37.3")
    implementation("org.jetbrains.exposed", "exposed-dao", "0.37.3")
    implementation("org.jetbrains.exposed", "exposed-jdbc", "0.37.3")
    implementation("org.xerial:sqlite-jdbc:3.36.0.2")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf-jvm:1.3.2")

    // Search: https://solr.apache.org/guide/8_11/using-solrj.html
    implementation("org.apache.solr:solr-solrj:8.11.1")

    // Content Analysis: https://tika.apache.org/2.1.0/gettingstarted.html
    implementation("org.apache.tika:tika-core:2.1.0")
    implementation("org.apache.tika:tika-parsers-standard-package:2.1.0")

    // Logging: https://www.kotlinresources.com/library/kotlin-logging/
    implementation("io.github.microutils:kotlin-logging:2.1.21")
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("ch.qos.logback:logback-classic:1.2.10")
    implementation("ch.qos.logback:logback-core:1.2.10")
    implementation("com.lordcodes.turtle:turtle:0.6.0")
    implementation("org.apache.james:apache-mime4j-core:0.8.5")
    implementation("org.apache.james:apache-mime4j-dom:0.8.5")

    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-serialization:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("org.bouncycastle:bcprov-jdk15on:1.69")
    implementation("com.sksamuel.hoplite:hoplite-core:1.4.16")
    implementation("com.sksamuel.hoplite:hoplite-yaml:1.4.16")
    implementation("org.kodein.di:kodein-di:7.10.0")
    implementation("org.kodein.di:kodein-di-framework-ktor-server-jvm:7.10.0")


    testImplementation("io.ktor:ktor-server-tests:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}