val kotlin_version: String by project
val kotlin_logging_version: String by project
val hoplite_version: String by project
val exposed_version: String by project
val kodein_version: String by project

plugins {
    kotlin("jvm") version "1.7.20"
}

dependencies {
    implementation(project(":core:content"))
    implementation(project(":core:event"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:security"))
    implementation(project(":core:serialization"))
    implementation(project(":core:search"))
    implementation(project(":core:storage"))
    implementation("io.github.microutils:kotlin-logging:$kotlin_logging_version")
    implementation("com.sksamuel.hoplite:hoplite-core:$hoplite_version")
    implementation("com.sksamuel.hoplite:hoplite-yaml:$hoplite_version")
    implementation("org.kodein.di:kodein-di:$kodein_version")
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}