import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.archivesName

val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val exposed_version: String by project
val kotlinx_serialization_version: String by project
val kotlin_logging_version:String by project
val slf4j_version: String by project
val mime4j_version: String by project
val bcprov_version: String by project
val hoplite_version: String by project
val kodein_version: String by project
val kotlinx_cli_version: String by project


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
    implementation(project(":core"))
    implementation(project(":util"))
    implementation(project(":io"))
    implementation(project(":serialization"))
    implementation(project(":system"))
    implementation(project(":security"))
    implementation(project(":event"))
    implementation(project(":model"))
    implementation(project(":content"))
    implementation(project(":search"))
    implementation(project(":storage"))
    implementation(project(":network"))

    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinx_serialization_version")

    // Logging: https://www.kotlinresources.com/library/kotlin-logging/
    implementation("io.github.microutils:kotlin-logging:$kotlin_logging_version")
    implementation("org.slf4j:slf4j-api:$slf4j_version")
    implementation("org.apache.james:apache-mime4j-core:$mime4j_version")
    implementation("org.apache.james:apache-mime4j-dom:$mime4j_version")

    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("ch.qos.logback:logback-core:$logback_version")
    implementation("org.bouncycastle:bcprov-jdk15on:$bcprov_version")
    implementation("org.kodein.di:kodein-di:$kodein_version")
    implementation("org.kodein.di:kodein-di-framework-ktor-server-jvm:$kodein_version")
    implementation("org.jetbrains.kotlinx:kotlinx-cli:$kotlinx_cli_version")

    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-network-tls-certificates-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-status-pages:$ktor_version")
    implementation("io.ktor:ktor-server-cors:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("io.ktor:ktor-server-html-builder:$ktor_version")
    implementation("io.ktor:ktor-server-sessions:$ktor_version")
    implementation("io.ktor:ktor-client-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-cio-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-serialization-jvm:$ktor_version")

    testImplementation(project(":io"))
    testImplementation(project(":relay"))
    testImplementation(project(":relay:client"))
    testImplementation(project(":relay:server"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktor_version")

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