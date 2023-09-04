val kotlinVersion: String by project
val kotlinxCoroutinesVersion: String by project
val ktorVersion: String by project
val kotlinLoggingVersion:String by project
val protobufVersion: String by project
val logbackVersion: String by project

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
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation(project(":core:util"))
    implementation(project(":core:serialization"))
    implementation(project(":core:security"))
    implementation(project(":core:model"))
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")

    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
    implementation("io.ktor:ktor-network-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets-jvm:$ktorVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
    testImplementation("ch.qos.logback:logback-classic:$logbackVersion")
}