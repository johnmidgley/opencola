val kotlin_version: String by project
val kotlin_logging_version:String by project
val lucene_version: String by project

plugins {
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.serialization") version "1.6.0"
}

dependencies {
    implementation(project(":core:util"))
    implementation(project(":core:io"))
    implementation(project(":core:serialization"))
    implementation(project(":core:security"))
    implementation(project(":core:model"))

    implementation("io.github.microutils:kotlin-logging:$kotlin_logging_version")
    implementation("org.apache.lucene:lucene-core:$lucene_version")
    implementation("org.apache.lucene:lucene-queryparser:$lucene_version")
    implementation("org.apache.lucene:lucene-backward-codecs:$lucene_version")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}