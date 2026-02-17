package io.opencola.tools.export

import io.opencola.security.hash.Sha256Hash
import io.opencola.security.keystore.defaultPasswordHash
import mu.KotlinLogging
import java.nio.file.Path
import java.nio.file.Paths

private val logger = KotlinLogging.logger("Main")

fun main(args: Array<String>) {
    val config = parseArgs(args)

    logger.info { "OpenCola State Export Tool" }
    logger.info { "Storage path: ${config.storagePath}" }
    logger.info { "Server port: ${config.port}" }

    val storage = openStorage(config)

    val personas = storage.getPersonas()
    val peers = storage.getPeers()
    logger.info { "Found ${personas.size} personas and ${peers.size} peers" }

    for (persona in personas) {
        logger.info { "  Persona: ${persona.name} (${persona.entityId}) active=${persona.isActive}" }
    }
    for (peer in peers) {
        logger.info { "  Peer: ${peer.name} (${peer.entityId})" }
    }

    println()
    println("Starting web UI at http://localhost:${config.port}")
    println("Open in your browser to select authorities and export data.")
    println()

    val server = ExportServer(storage, config.port, config.outputDir)
    server.start(openBrowser = true)
}

data class Config(
    val storagePath: Path,
    val port: Int,
    val outputDir: Path
)

fun openStorage(config: Config): StorageAccess {
    // First try with the default password
    try {
        return StorageAccess(config.storagePath, defaultPasswordHash)
    } catch (e: java.io.IOException) {
        if (e.message?.contains("wrong password") != true && e.message?.contains("mac invalid") != true) {
            throw e
        }
    }

    // Default password didn't work - prompt the user
    val console = System.console()
    if (console != null) {
        // Interactive terminal - use Console.readPassword() which hides input
        repeat(3) {
            val chars = console.readPassword("Enter keystore password: ")
            if (chars != null) {
                val hash = Sha256Hash.ofString(String(chars))
                chars.fill(' ') // clear password from memory
                try {
                    return StorageAccess(config.storagePath, hash)
                } catch (e: java.io.IOException) {
                    if (e.message?.contains("wrong password") == true || e.message?.contains("mac invalid") == true) {
                        System.err.println("Incorrect password. Please try again.")
                    } else {
                        throw e
                    }
                }
            }
        }
    } else {
        // No Console available (e.g. running via Gradle) - use stty to hide input
        for (attempt in 1..3) {
            print("Enter keystore password: ")
            System.out.flush()
            val line = readPasswordFromStdin()
            println() // newline after hidden input
            if (line.isNullOrEmpty()) {
                System.err.println("No input received. Make sure stdin is connected.")
                break
            }
            val hash = Sha256Hash.ofString(line)
            try {
                return StorageAccess(config.storagePath, hash)
            } catch (e: java.io.IOException) {
                if (e.message?.contains("wrong password") == true || e.message?.contains("mac invalid") == true) {
                    System.err.println("Incorrect password. Please try again.")
                } else {
                    throw e
                }
            }
        }
    }

    System.err.println("Too many failed password attempts.")
    kotlin.system.exitProcess(1)
}

/**
 * Reads a line from stdin with echo disabled via stty.
 * Falls back to plain readlnOrNull if stty is not available.
 */
fun readPasswordFromStdin(): String? {
    try {
        // Disable terminal echo
        ProcessBuilder("stty", "-echo")
            .inheritIO()
            .start()
            .waitFor()
    } catch (_: Exception) {
        // stty not available - fall back to visible input
        return readlnOrNull()
    }

    return try {
        readlnOrNull()
    } finally {
        // Always re-enable echo
        try {
            ProcessBuilder("stty", "echo")
                .inheritIO()
                .start()
                .waitFor()
        } catch (_: Exception) {
            // best effort
        }
    }
}

fun parseArgs(args: Array<String>): Config {
    var storagePath: Path? = null
    var port = 8090
    var outputDir = Paths.get("export")

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--storage-path", "-s" -> {
                i++
                storagePath = Paths.get(args[i])
            }
            "--port" -> {
                i++
                port = args[i].toInt()
            }
            "--output", "-o" -> {
                i++
                outputDir = Paths.get(args[i])
            }
            "--help", "-h" -> {
                printUsage()
                kotlin.system.exitProcess(0)
            }
            else -> {
                System.err.println("Unknown argument: ${args[i]}")
                printUsage()
                kotlin.system.exitProcess(1)
            }
        }
        i++
    }

    if (storagePath == null) {
        storagePath = defaultStoragePath()
    }

    if (!storagePath.toFile().exists()) {
        System.err.println("Storage path does not exist: $storagePath")
        kotlin.system.exitProcess(1)
    }

    return Config(storagePath, port, outputDir)
}

fun defaultStoragePath(): Path {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("mac") -> {
            val home = System.getProperty("user.home")
            Paths.get(home, "Library", "Application Support", "OpenCola", "storage")
        }
        os.contains("win") -> {
            val appData = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
            Paths.get(appData, "OpenCola", "storage")
        }
        else -> {
            val home = System.getProperty("user.home")
            Paths.get(home, ".opencola", "storage")
        }
    }
}

fun printUsage() {
    println("""
        OpenCola State Export Tool

        Usage: export-state [options]

        Options:
          --storage-path, -s <path>  Path to OpenCola storage directory
                                     Default: OS-specific (~/Library/Application Support/OpenCola/storage on macOS)
          --port <port>              Web server port (default: 8090)
          --output, -o <path>        Default export output directory (default: ./export)
          --help, -h                 Show this help

        The keystore password will be prompted securely at startup if needed.
    """.trimIndent())
}
