package io.opencola.core.system

import io.opencola.core.extensions.runCommand
import mu.KotlinLogging
import java.awt.Desktop
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.exists

private val logger = KotlinLogging.logger("oc.system")

enum class OS {
    Unknown,
    Linux,
    Mac,
    Windows,
}

fun getOS() : OS {
    val os = System.getProperty("os.name").lowercase()

    return if(os.contains("mac"))
        OS.Mac
    else if(os.contains("windows"))
        OS.Windows
    else if(os.contains("linux"))
        OS.Linux
    else
        OS.Unknown
}

fun openFile(path: Path) {
    when(getOS()) {
        OS.Mac -> "open ${path.fileName}".runCommand(path.parent)
        OS.Windows -> "start ${path.fileName}".runCommand(path.parent)
        else -> logger.warn { "Don't know how to open $path on this os" }
    }
}

fun openUriLinux(uri: URI) {
    try {
        "xdg-open $uri".runCommand()
        return
    } catch(e: Throwable) {}

    try {
        Desktop.getDesktop().browse(uri)
        return
    } catch (e: Throwable) {}

    throw RuntimeException("Unable to open $uri on Linux")
}

fun openUri(uri: URI) {
    when(getOS()) {
        OS.Mac -> "open $uri".runCommand()
        OS.Windows -> "explorer $uri".runCommand()
        OS.Linux -> openUriLinux(uri)
        else -> logger.warn { "Don't know how to open $uri on this os" }
    }
}

fun autoStartMac() {
    val args = listOf("osascript", "-e", "tell application \"System Events\" to make login item at end with properties {path:\"/Applications/OpenCola.app\", hidden:false}")
    val result = runCommand(args).joinToString("\n")
    logger.info { "Auto start result: $result" }
}

fun autoStartLinux() {
    val opencolaServerPath = ((Path.of({}.javaClass.protectionDomain.codeSource.location.path).parent).parent).parent
        .resolve("bin")
        .resolve("opencola")

    if(!opencolaServerPath.exists()) {
        logger.warn { "Can't find opencola-server path for autostart. Tried $opencolaServerPath" }
        return
    }

    val deskTopEntry = """
        [Desktop Entry]
        Type=Application
        Exec=/home/john/dev/opencola/install/linux/opencola/bin/opencola
        Hidden=false
        NoDisplay=false
        X-GNOME-Autostart-enabled=true
        Name[en_US]=OpenCola
        Name=OpenCola
        Comment[en_US]=
        Comment=
    """.trimIndent()

    Path
        .of(System.getProperty("user.home"), ".config", "autostart", "opencola.desktop")
        .toFile()
        .writeText(deskTopEntry)
}

fun autoStart() {
    when(getOS()) {
        OS.Mac -> autoStartMac()
        OS.Linux -> autoStartLinux()
        else -> logger.warn { "Don't know how to auto start on this os" }
    }
}