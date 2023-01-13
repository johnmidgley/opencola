package opencola.server

import io.opencola.core.config.*
import io.opencola.system.getOS
import mu.KotlinLogging

private val logger = KotlinLogging.logger("opencola")

fun main(args: Array<String>) {
    try {
        logger.info { "Starting..." }
        logger.info { "OS: ${System.getProperty("os.name")} - Detected ${getOS()}" }
        logger.info { "Args: ${args.joinToString(" ")}" }
        val commandLineArgs = CommandLineArgs(args)
        val storagePath = initStorage(commandLineArgs.storage)
        logger.info { "Storage path: $storagePath" }
        val config = loadConfig(storagePath.resolve("opencola-server.yaml"))
        logger.info { "Config: $config" }

        if (commandLineArgs.desktop)
            startDesktopApp(storagePath, config)
        else
            startServer(storagePath, config)
    } catch (e: Throwable) {
        logger.error { "FATAL: $e: ${e.stackTrace[0]}" }
    }
}
