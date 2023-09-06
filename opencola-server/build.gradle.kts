/*

DOCS
********************************************************
https://docs.gradle.org/current/userguide/userguide.html
********************************************************

*/
plugins {
    application
    kotlin("jvm") version "1.7.20"
}

// TODO: Pull allProjects properties out of sub projects
allprojects {
    group = "opencola"
    version = "1.3.0"

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    repositories {
        google()
        mavenCentral()
    }
}