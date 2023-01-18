val kotlin_version: String by project
val kotlin_logging_version: String by project
val exposed_version: String by project
val mime4j_version: String by project

plugins {
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.serialization") version "1.6.0"
}

dependencies {
    implementation(project(":util"))
    implementation(project(":serialization"))
    implementation(project(":model"))
    implementation(project(":event"))
    implementation(project(":security"))
    implementation(project(":content"))

    implementation("io.github.microutils:kotlin-logging:$kotlin_logging_version")
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.apache.james:apache-mime4j-dom:$mime4j_version")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}