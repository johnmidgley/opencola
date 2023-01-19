val kotlin_version: String by project
val ktor_version: String by project
val kotlin_logging_version:String by project
val kotlinx_serialization_version: String by project

plugins {
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.serialization") version "1.6.0"
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":util"))
    implementation(project(":serialization"))
    implementation(project(":security"))
    implementation(project(":model"))
    implementation(project(":event"))
    implementation(project(":storage"))
    implementation(project(":relay:client"))
    implementation(project(":relay:common"))
    implementation("io.github.microutils:kotlin-logging:$kotlin_logging_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:$kotlinx_serialization_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-client-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-okhttp:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}