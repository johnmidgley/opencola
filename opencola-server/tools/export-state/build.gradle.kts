val kotlinVersion: String by project
val ktorVersion: String by project
val logbackVersion: String by project
val kotlinLoggingVersion: String by project
val kotlinxSerializationVersion: String by project
val exposedVersion: String by project
val sqliteVersion: String by project

plugins {
    application
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.serialization") version "1.6.0"
}

application {
    mainClass.set("io.opencola.tools.export.MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("version.properties") {
        expand(props)
    }
}

dependencies {
    implementation(project(":core:util"))
    implementation(project(":core:io"))
    implementation(project(":core:serialization"))
    implementation(project(":core:security"))
    implementation(project(":core:model"))
    implementation(project(":core:storage"))
    implementation(project(":core:event"))
    implementation(project(":core:system"))
    implementation(project(":core:content"))

    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.xerial:sqlite-jdbc:$sqliteVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}
