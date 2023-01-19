val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project
plugins {
    kotlin("jvm") version "1.7.20"
}

dependencies {
    implementation(project(":relay:common"))
    implementation(project(":relay:server"))
    implementation(project(":relay:client"))
}