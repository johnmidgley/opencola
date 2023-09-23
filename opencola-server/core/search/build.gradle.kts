val kotlinVersion: String by project
val kotlinLoggingVersion:String by project
val luceneVersion: String by project
val mime4jVersion: String by project
val logbackVersion: String by project

plugins {
    kotlin("jvm") version "1.7.20"
}

dependencies {
    implementation(project(":core:util"))
    implementation(project(":core:io"))
    implementation(project(":core:serialization"))
    implementation(project(":core:security"))
    implementation(project(":core:model"))
    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
    implementation("org.apache.lucene:lucene-core:$luceneVersion")
    implementation("org.apache.lucene:lucene-queryparser:$luceneVersion")
    implementation("org.apache.lucene:lucene-backward-codecs:$luceneVersion")
    implementation("org.apache.lucene:lucene-analysis-common:$luceneVersion")
    implementation(project(mapOf("path" to ":core:storage")))

    testImplementation(project(":test"))
    testImplementation(project(":core:content"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
    testImplementation("ch.qos.logback:logback-classic:$logbackVersion")
    testImplementation("org.apache.james:apache-mime4j-dom:$mime4jVersion")
}