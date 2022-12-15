rootProject.name = "opencola-server"
include("core")
include("relay")

pluginManagement {
  repositories {
    gradlePluginPortal()
    maven("https://maven.pkg.jetnbrains.space/public/p/compose/dev")
  }
}