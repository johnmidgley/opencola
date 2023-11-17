val kotlinVersion: String by project
val kodeinVersion: String by project
val kotlinxCliVersion: String by project
val logbackVersion: String by project
val kotlinxCoroutinesVersion: String by project
val kotlinxSerializationVersion: String by project

plugins {
    application
    kotlin("jvm") version "1.9.0"
}

application {
    applicationName = "ocr"
}

application {
    mainClass.set("io.opencola.relay.cli.OcrKt")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:security"))
    implementation(project(":core:serialization"))
    implementation(project(":core:storage"))
    implementation(project(":relay:client"))
    implementation(project(":relay:common"))

    implementation("com.github.ajalt.clikt:clikt:4.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")

    // This avoids "No SLF4J providers were found" warning (also lets background logging work)
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}

