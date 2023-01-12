rootProject.name = "opencola-server"
include("util")
include("core")
include("relay")
include("server")

pluginManagement {
  repositories {
    gradlePluginPortal()
    maven("https://maven.pkg.jetnbrains.space/public/p/compose/dev")
  }
}