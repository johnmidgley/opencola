val kotlinVersion: String by project
val kodeinVersion: String by project

// TODO: jvm version should be set as project property
plugins {
    kotlin("jvm") version "1.7.20"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:security"))
    implementation(project(":core:search"))
    implementation(project(":core:application"))
    implementation("org.kodein.di:kodein-di:$kodeinVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}