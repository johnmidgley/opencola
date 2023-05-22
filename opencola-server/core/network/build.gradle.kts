val kotlinVersion: String by project
val ktorVersion: String by project
val kotlinLoggingVersion:String by project
val kotlinxSerializationVersion: String by project
val protobufVersion: String by project
val kodeinVersion: String by project
val logbackVersion: String by project
val exposedVersion: String by project

plugins {
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.serialization") version "1.6.0"
}

dependencies {
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    implementation(kotlin("stdlib"))
    implementation(project(":core:util"))
    implementation(project(":core:serialization"))
    implementation(project(":core:security"))
    implementation(project(":core:model"))
    implementation(project(":core:event"))
    implementation(project(":core:storage"))
    implementation(project(":relay:client"))
    implementation(project(":relay:common"))

    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:$kotlinxSerializationVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation("org.kodein.di:kodein-di:$kodeinVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
    testImplementation(project(":test"))
    testImplementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
}