val kotlinVersion: String by project
val kodeinVersion: String by project
val kotlinLoggingVersion:String by project
val slf4jVersion: String by project
val ktorVersion: String by project
val exposedVersion: String by project

plugins {
    kotlin("jvm") version "1.7.20"
}

dependencies {
    implementation(project(":core:io"))
    implementation(project(":core:serialization"))
    implementation(project(":core:model"))
    implementation(project(":core:security"))
    implementation(project(":core:storage"))
    implementation(project(":core:event"))
    implementation(project(":core:search"))
    implementation(project(":core:network"))
    implementation(project(":core:application"))
    implementation(project(":relay:common"))
    implementation(project(":relay:server"))
    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.kodein.di:kodein-di:$kodeinVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}