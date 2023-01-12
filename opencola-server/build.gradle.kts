import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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

/*

DOCS
********************************************************
https://docs.gradle.org/current/userguide/userguide.html
********************************************************

*/
plugins {
    application
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.serialization") version "1.6.0"
    id("org.jetbrains.compose") version "1.2.2"
}

// TODO: Pull allProjects properties out of sub projects
allprojects {
    group = "opencola"
    version = "1.1.6"

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    repositories {
        google()
        mavenCentral()
        flatDir {
            name = "localRepository"
            dirs("${project.rootDir}/../lib")
        }
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}