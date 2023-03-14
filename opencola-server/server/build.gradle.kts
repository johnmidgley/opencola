import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.archivesName

val ktorVersion: String by project
val kotlinVersion: String by project
val logbackVersion: String by project
val exposedVersion: String by project
val kotlinxSerializationVersion: String by project
val kotlinLoggingVersion:String by project
val slf4jVersion: String by project
val mime4jVersion: String by project
val bcprovVersion: String by project
val hopliteVersion: String by project
val kodeinVersion: String by project
val kotlinxCliVersion: String by project


plugins {
    application
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.serialization") version "1.6.0"
    id("org.jetbrains.compose") version "1.2.2"
}

apply {
    plugin("java")
}

application {
    applicationName = "opencola-server"
}

// TODO: This should be settable through the java plugin archivesBaseName property, but nothing seems to work
//  https://docs.gradle.org/7.4.1/userguide/java_plugin.html
archivesName.set("opencola-server")


// TODO: Move this application to a separate project. This level should be for all projects.
application {
    mainClass.set("opencola.server.ApplicationKt")
}

dependencies {
    implementation(project(":core:util"))
    implementation(project(":core:io"))
    implementation(project(":core:serialization"))
    implementation(project(":core:system"))
    implementation(project(":core:security"))
    implementation(project(":core:event"))
    implementation(project(":core:model"))
    implementation(project(":core:content"))
    implementation(project(":core:search"))
    implementation(project(":core:storage"))
    implementation(project(":core:network"))
    implementation(project(":core:application"))

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")

    // Logging: https://www.kotlinresources.com/library/kotlin-logging/
    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("org.apache.james:apache-mime4j-core:$mime4jVersion")
    implementation("org.apache.james:apache-mime4j-dom:$mime4jVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("ch.qos.logback:logback-core:$logbackVersion")
    implementation("org.bouncycastle:bcprov-jdk15on:$bcprovVersion")
    implementation("org.kodein.di:kodein-di:$kodeinVersion")
    implementation("org.kodein.di:kodein-di-framework-ktor-server-jvm:$kodeinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-cli:$kotlinxCliVersion")

    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-network-tls-certificates-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-html-builder:$ktorVersion")
    implementation("io.ktor:ktor-server-sessions-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization-jvm:$ktorVersion")

    testImplementation(project(":test"))
    testImplementation(project(":core:io"))
    testImplementation(project(":relay"))
    testImplementation(project(":relay:client"))
    testImplementation(project(":relay:server"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    implementation(compose.desktop.currentOs)
}

fun getTarget(): TargetFormat {
    val os = System.getProperty("os.name").toLowerCase()

    return  if(os.contains("mac"))
        TargetFormat.Dmg
    else if(os.contains("win"))
        TargetFormat.Msi
    else if(os.contains("nux"))
        TargetFormat.Pkg
    else
        TargetFormat.AppImage
}

compose.desktop {
//    application {
//        mainClass = "opencola.server.ApplicationKt"
//
//        nativeDistributions {
//            macOS {
//                iconFile.set(File("src/main/resources/icons/opencola.icns"))
//            }
//            packageName = "OpenCola"
//            args += listOf("--desktop")
//            includeAllModules = true
//            targetFormats(getTarget())
//        }
//    }
}