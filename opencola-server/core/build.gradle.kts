val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val sqlite_version: String by project
val exposed_version: String by project
val kotlinx_serialization_version: String by project
val solrj_version: String by project
val tika_version: String by project
val kotlin_logging_version:String by project
val slf4j_version: String by project
val mime4j_version: String by project
val bcprov_version: String by project
val hoplite_version: String by project
val kodein_version: String by project
val jsoup_version: String by project
val lucene_version: String by project

plugins {
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.serialization") version "1.6.0"
}

group = "io.opencola"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    flatDir{
        name = "localRepository"
        dirs("${project.rootDir}/../lib")
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":util"))
    implementation(project(":io"))
    implementation(project(":serialization"))
    implementation(project(":system"))
    implementation(project(":security"))
    implementation(project(":model"))
    implementation(project(":content"))
    implementation(project(":search"))

    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")

    implementation("org.xerial:sqlite-jdbc:$sqlite_version")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinx_serialization_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf-jvm:$kotlinx_serialization_version")

    // Search: https://solr.apache.org/guide/8_11/using-solrj.html
    implementation("org.apache.solr:solr-solrj:$solrj_version")

    // Content Analysis: https://tika.apache.org/2.1.0/gettingstarted.html
    implementation("org.apache.tika:tika-core:$tika_version")
    implementation("org.apache.tika:tika-parsers-standard-package:$tika_version")
    implementation("org.apache.pdfbox:pdfbox:2.0.27")

    // Logging: https://www.kotlinresources.com/library/kotlin-logging/
    implementation("io.github.microutils:kotlin-logging:$kotlin_logging_version")
    implementation("org.slf4j:slf4j-api:$slf4j_version")
    implementation("org.apache.james:apache-mime4j-core:$mime4j_version")
    implementation("org.apache.james:apache-mime4j-dom:$mime4j_version")

    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("ch.qos.logback:logback-core:$logback_version")
    implementation("org.bouncycastle:bcprov-jdk15on:$bcprov_version")
    implementation("com.sksamuel.hoplite:hoplite-core:$hoplite_version")
    implementation("com.sksamuel.hoplite:hoplite-yaml:$hoplite_version")
    implementation("org.kodein.di:kodein-di:$kodein_version")
    implementation("org.kodein.di:kodein-di-framework-ktor-server-jvm:$kodein_version")
    implementation("org.jsoup:jsoup:$jsoup_version")
    implementation("org.apache.lucene:lucene-core:$lucene_version")
    implementation("org.apache.lucene:lucene-queryparser:$lucene_version")
    implementation("org.apache.lucene:lucene-backward-codecs:$lucene_version")
    implementation("com.lordcodes.turtle:turtle:0.8.0")

    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-client-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-cio-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-okhttp:$ktor_version")
    implementation("io.ktor:ktor-client-serialization-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-utils-jvm:$ktor_version")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktor_version")
}