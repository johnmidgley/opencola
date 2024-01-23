/*
 * Copyright 2024 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.opencola.system

import mu.KotlinLogging
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

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

fun openUriLinux(uri: URI) {
    try {
        runCommand(listOf("xdg-open", uri.toString()))
        return
    } catch(_: Throwable) {}

    try {
        Desktop.getDesktop().browse(uri)
        return
    } catch (_: Throwable) {}

    throw RuntimeException("Unable to open $uri on Linux")
}

fun openUri(uri: URI) {
    when(getOS()) {
        OS.Mac -> runCommand(listOf("open", uri.toString()))
        OS.Windows -> Desktop.getDesktop().browse(uri) // "explorer $uri".runCommand()
        OS.Linux -> openUriLinux(uri)
        else -> logger.warn { "Don't know how to open $uri on this os" }
    }
}

fun autoStartMac() {
    // To reset permissions: tccutil reset All opencola.server
    val args = listOf("osascript", "-e", "tell application \"System Events\" to make login item at end with properties {path:\"/Applications/OpenCola.app\", hidden:false}")
    val result = runCommand(args).joinToString("\n")
    logger.info { "Auto start result: $result" }
}

fun autoStartLinux() {
    val openColaServerPath = ((Path.of({}.javaClass.protectionDomain.codeSource.location.path).parent).parent).parent
        .resolve("bin")
        .resolve("opencola")

    if(!openColaServerPath.exists()) {
        logger.warn { "Can't find opencola-server path for autostart. Tried $openColaServerPath" }
        return
    }

    val deskTopEntry = """
        [Desktop Entry]
        Type=Application
        Exec=${openColaServerPath}
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

fun getAppJarPath() : Path {
    return File({}.javaClass.protectionDomain.codeSource.location.toURI()).toPath()
}

fun autoStartWindows() {
    val exePath = getAppJarPath().parent.parent.resolve("OpenCola.exe")

    if(!exePath.exists()) {
        logger.warn { "Can't locate OpenCola.exe - unable to auto start" }
        return
    }

    val command = """
        cd %userprofile%\\Start Menu\\Programs\\Startup
        del opencola.lnk"
        mklink "%userprofile%\\Start Menu\\Programs\\Startup\\opencola.lnk" "C:\\Program Files\\OpenCola\\OpenCola.exe"
    """.trimIndent()
    val autoStartBat = kotlin.io.path.createTempFile(suffix = "autostart.bat").also { it.writeText(command) }
    val result = runCommand(listOf(autoStartBat.toString())).joinToString("\n")
    logger.info { "autoStartWindows: $result" }
}

// osascript -e 'tell application "Terminal" to do script "cd /Users/johnmidgley/Library/Application*Support/OpenCola/storage/cert && ./install-cert"'

fun autoStart() : Boolean {
    try {
        when (getOS()) {
            OS.Mac -> autoStartMac()
            OS.Windows -> autoStartWindows()
            OS.Linux -> autoStartLinux()
            else -> logger.warn { "Don't know how to auto start on this os" }
        }
    } catch (e: Throwable) {
        logger.error(e) { "Error auto starting" }
        return false
    }

    return true
}