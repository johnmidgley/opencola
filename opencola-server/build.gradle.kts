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
    version = "1.3.3"

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