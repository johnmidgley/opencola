rootProject.name = "opencola-server"
include("util")
include("serialization")
include("io")
include("system")
include("security")
include("model")
include("content")
include("search")
include("core")
include("relay")
include("server")

pluginManagement {
  repositories {
    gradlePluginPortal()
    maven("https://maven.pkg.jetnbrains.space/public/p/compose/dev")
  }
}