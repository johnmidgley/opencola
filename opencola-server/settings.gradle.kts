rootProject.name = "opencola-server"

include("util")
include("serialization")
include("io")
include("system")
include("security")
include("storage")
include("event")
include("model")
include("content")
include("search")
include("core")
include("relay")
include("server")
include("relay:common")
include("relay:server")
include("relay:client")

project(":server").name = "opencola-server"

pluginManagement {
  repositories {
    gradlePluginPortal()
    maven("https://maven.pkg.jetnbrains.space/public/p/compose/dev")
  }
}