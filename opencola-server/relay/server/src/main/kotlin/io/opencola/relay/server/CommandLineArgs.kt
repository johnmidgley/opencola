package io.opencola.relay.server

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType

class CommandLineArgs(args: Array<String>) {
    private val parser = ArgParser("relay")
    val storage by parser.option(ArgType.String, shortName = "s", description = "Storage path")

    init {
        // https://github.com/Kotlin/kotlinx-cli
        parser.parse(args)
    }
}