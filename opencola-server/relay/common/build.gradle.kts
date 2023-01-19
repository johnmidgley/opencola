val kotlin_version: String by project
val kotlinx_coroutines_version: String by project
val ktor_version: String by project
val kotlin_logging_version:String by project

plugins {
    kotlin("jvm") version "1.7.20"
}

repositories {
    mavenCentral()
    flatDir{
        name = "localRepository"
        dirs("${project.rootDir}/../lib")
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":core:util"))
    implementation(project(":core:serialization"))
    implementation(project(":core:security"))
    implementation(project(":core:model"))

    implementation("io.github.microutils:kotlin-logging:$kotlin_logging_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinx_coroutines_version")
    implementation("io.ktor:ktor-network:$ktor_version")
    implementation("io.ktor:ktor-websockets:$ktor_version")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}