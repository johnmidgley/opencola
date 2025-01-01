val kotlinVersion: String by project
val logbackVersion: String by project
val hopliteVersion: String by project
val cliktVersion: String by project
val exposedVersion: String by project

plugins {
    application
    kotlin("jvm") version "1.9.0"
}

application {
    applicationName = "oc"
}

application {
    mainClass.set("io.opencola.cli.OcKt")
}

dependencies {
    // This avoids "No SLF4J providers were found" warning (also lets background logging work)
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("com.sksamuel.hoplite:hoplite-core:$hopliteVersion")
    implementation("com.github.ajalt.clikt:clikt:$cliktVersion")
    implementation(project(":core:model"))
    implementation(project(":core:serialization"))
    implementation(project(":core:security"))
    implementation(project(":core:storage"))
    implementation(project(":core:event"))
    implementation(project(":core:application"))

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}

