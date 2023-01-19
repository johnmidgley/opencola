val kotlin_version: String by project
val kotlin_logging_version: String by project
val hoplite_version: String by project
val exposed_version: String by project
val kodein_version: String by project

plugins {
    kotlin("jvm") version "1.7.20"
}

dependencies {
    implementation(project(":content"))
    implementation(project(":event"))
    implementation(project(":model"))
    implementation(project(":network"))
    implementation(project(":security"))
    implementation(project(":serialization"))
    implementation(project(":search"))
    implementation(project(":storage"))
    implementation("io.github.microutils:kotlin-logging:$kotlin_logging_version")
    implementation("com.sksamuel.hoplite:hoplite-core:$hoplite_version")
    implementation("com.sksamuel.hoplite:hoplite-yaml:$hoplite_version")
    implementation("org.kodein.di:kodein-di:$kodein_version")
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}