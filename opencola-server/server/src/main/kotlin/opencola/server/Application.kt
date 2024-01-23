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

package opencola.server

import io.opencola.application.*
import io.opencola.system.getOS
import mu.KotlinLogging

private val logger = KotlinLogging.logger("opencola")

fun main(args: Array<String>) {
    try {
        logger.info { "Starting..." }
        logger.info { "OS: ${System.getProperty("os.name")} - Detected ${getOS()}" }
        logger.info { "Version: $OC_VERSION" }
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
