val kotlin_version: String by project

// TODO: Can probably remove these, as they're only used in content
val jsoup_version: String by project
val mime4j_version: String by project
val tika_version: String by project

plugins {
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.serialization") version "1.6.0"
}

dependencies {
    implementation(project(":core:util"))
    implementation("org.jsoup:jsoup:$jsoup_version")
    implementation("org.apache.james:apache-mime4j-core:$mime4j_version")
    implementation("org.apache.james:apache-mime4j-dom:$mime4j_version")
    implementation("org.apache.tika:tika-core:$tika_version")
    implementation("org.apache.tika:tika-parsers-standard-package:$tika_version")
    implementation("org.apache.pdfbox:pdfbox:2.0.27")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}