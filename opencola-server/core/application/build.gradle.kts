val kotlinVersion: String by project
val kotlinLoggingVersion: String by project
val hopliteVersion: String by project
val exposedVersion: String by project
val kodeinVersion: String by project
val protobufVersion: String by project

plugins {
    kotlin("jvm") version "1.9.0"
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
    implementation(project(":relay:common"))
    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
    implementation("com.sksamuel.hoplite:hoplite-core:$hopliteVersion")
    implementation("com.sksamuel.hoplite:hoplite-yaml:$hopliteVersion")
    implementation("org.kodein.di:kodein-di:$kodeinVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")

    testImplementation(project(":test"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}