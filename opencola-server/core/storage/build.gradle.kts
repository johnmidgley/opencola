val kotlinVersion: String by project
val kotlinLoggingVersion: String by project
val exposedVersion: String by project
val mime4jVersion: String by project

plugins {
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.serialization") version "1.6.0"
}

dependencies {
    implementation(project(":core:system"))
    implementation(project(":core:util"))
    implementation(project(":core:serialization"))
    implementation(project(":core:model"))
    implementation(project(":core:event"))
    implementation(project(":core:security"))
    implementation(project(":core:content"))

    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.apache.james:apache-mime4j-dom:$mime4jVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
    testImplementation(project(":test"))
}