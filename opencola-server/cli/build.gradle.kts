val kotlinVersion: String by project
val kodeinVersion: String by project
val kotlinxCliVersion: String by project
val logbackVersion: String by project

plugins {
    application
    kotlin("jvm") version "1.7.20"
}

application {
    applicationName = "oc"
}

application {
    mainClass.set("io.opencola.cli.OcKt")
}

dependencies {
    implementation(project(":core:io"))
    implementation(project(":core:serialization"))
    implementation(project(":core:security"))
    implementation(project(":core:model"))
    implementation(project(":core:storage"))
    implementation(project(":core:search"))
    implementation(project(":core:application"))
    // This avoids "No SLF4J providers were found" warning (also lets background logging work)
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.kodein.di:kodein-di:$kodeinVersion")
    implementation("org.kodein.di:kodein-di-framework-ktor-server-jvm:$kodeinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-cli:$kotlinxCliVersion")
    implementation(project(":test"))

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}

