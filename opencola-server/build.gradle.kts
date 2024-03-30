import com.github.jk1.license.render.ReportRenderer
import com.github.jk1.license.render.InventoryHtmlReportRenderer
import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.filter.LicenseBundleNormalizer

val ktorVersion: String by project
val kotlinVersion: String by project
val logbackVersion: String by project
val exposedVersion: String by project
val kotlinxSerializationVersion: String by project
val kotlinLoggingVersion: String by project
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
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.serialization") version "1.6.0"
    id("com.github.jk1.dependency-license-report") version "2.5"
}

// TODO: Pull allProjects properties out of sub projects
allprojects {
    group = "opencola"
    // NOTE: When this is updated, make sure to re-package and run
    // opencola/extension/chrome/deploy
    version = "1.4.0"

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

licenseReport {
    renderers = arrayOf<ReportRenderer>(InventoryHtmlReportRenderer("report.html", "Backend"))
    filters = arrayOf<DependencyFilter>(LicenseBundleNormalizer())
}