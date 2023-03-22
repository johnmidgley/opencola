rootProject.name = "opencola-server"

include("core")
include("core:util")
include("core:serialization")
include("core:io")
include("core:system")
include("core:security")
include("core:storage")
include("core:event")
include("core:model")
include("core:content")
include("core:search")
include("core:network")
include("core:application")
include("relay")
include("server")
include("relay:common")
include("relay:server")
include("relay:client")
include("cli")
include("test")

project(":server").name = "opencola-server"

pluginManagement {
  repositories {
    gradlePluginPortal()
    maven("https://maven.pkg.jetnbrains.space/public/p/compose/dev")
  }
}