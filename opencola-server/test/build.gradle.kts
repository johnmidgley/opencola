val kotlinVersion: String by project
val kodeinVersion: String by project
val kotlinLoggingVersion:String by project
val slf4jVersion: String by project
val ktorVersion: String by project
val exposedVersion: String by project


// TODO: jvm version should be set as project property
plugins {
    kotlin("jvm") version "1.7.20"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core:serialization"))
    implementation(project(":core:model"))
    implementation(project(":core:security"))
    implementation(project(":core:storage"))
    implementation(project(":core:event"))
    implementation(project(":core:search"))
    implementation(project(":core:network"))
    implementation(project(":core:application"))
    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.kodein.di:kodein-di:$kodeinVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation(project(mapOf("path" to ":core:io")))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}