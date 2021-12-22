val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project

plugins {
    application
    kotlin("jvm") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.0"
}

group = "opencola"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("opencola.server.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.1")

    // Search: https://solr.apache.org/guide/8_11/using-solrj.html
    implementation("org.apache.solr:solr-solrj:8.11.0")

    // Content Analysis: https://tika.apache.org/2.1.0/gettingstarted.html
    implementation("org.apache.tika:tika-core:2.1.0")
    implementation("org.apache.tika:tika-parsers-standard-package:2.1.0")

    // Logging: https://www.kotlinresources.com/library/kotlin-logging/
    implementation("io.github.microutils:kotlin-logging:2.1.16")
    implementation("org.slf4j:slf4j-api:1.7.32")
    implementation("ch.qos.logback:logback-classic:1.2.9")
    implementation("ch.qos.logback:logback-core:1.2.9")
    implementation("com.lordcodes.turtle:turtle:0.6.0")
    implementation("org.apache.james:apache-mime4j-core:0.8.5")
    implementation("org.apache.james:apache-mime4j-dom:0.8.5")

    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-serialization:$ktor_version")
    testImplementation("io.ktor:ktor-server-tests:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}